package com.pawcycle.backend.subscription.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.infra.SkuRepository;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.infra.MemberRepository;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class V2SubscriptionCommandIntegrationTests {

	@Autowired private V2SubscriptionService service;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private MemberRepository members;
	@Autowired private ProductRepository products;
	@Autowired private SkuRepository skus;
	@Autowired private PasswordEncoder passwordEncoder;

	private Member member;
	private long planVersionId;

	@BeforeEach
	void setUp() {
		member = members.saveAndFlush(new Member("v2-command-" + UUID.randomUUID() + "@example.test", passwordEncoder.encode("test-password")));
		Product product = products.saveAndFlush(new Product("V2 command product", "test", null, "DOG", null, "PUBLIC"));
		Sku sku = skus.saveAndFlush(new Sku(product, "v2-command-sku-" + UUID.randomUUID(), new BigDecimal("12000.00"), true, 1));
		jdbc.update("INSERT INTO subscription_plans(name,target_pet_type,on_sale) VALUES (?,?,true)", "DOG command plan", "DOG");
		long planId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update("INSERT INTO plan_versions(plan_id,package_price_krw,is_migration_only) VALUES (?,24000,false)", planId);
		planVersionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update("INSERT INTO plan_items(plan_version_id,sku_id,quantity) VALUES (?,?,2)", planVersionId, sku.getId());
		jdbc.update("INSERT INTO plan_version_delivery_cycles(plan_version_id,delivery_cycle_weeks) VALUES (?,4)", planVersionId);
		jdbc.update("UPDATE subscription_plans SET current_plan_version_id=? WHERE id=?", planVersionId, planId);
	}

	@Test
	void commandRequiresIfMatch() {
		long subscriptionId = createSubscription("missing-if-match");

		assertThatThrownBy(() -> service.command(member.getId(), subscriptionId, "pause", "missing-if-match", null, Map.of()))
				.isInstanceOf(V2ApiException.class)
				.hasFieldOrPropertyWithValue("code", "IF_MATCH_REQUIRED");
	}

	@Test
	void commandRejectsInvalidIfMatch() {
		long subscriptionId = createSubscription("invalid-if-match");

		assertThatThrownBy(() -> service.command(member.getId(), subscriptionId, "pause", "invalid-if-match", "0", Map.of()))
				.isInstanceOf(V2ApiException.class)
				.hasFieldOrPropertyWithValue("code", "IF_MATCH_INVALID");
	}

	@Test
	void commandRejectsStaleVersion() {
		long subscriptionId = createSubscription("stale-if-match");

		assertThatThrownBy(() -> service.command(member.getId(), subscriptionId, "pause", "stale-if-match", "\"1\"", Map.of()))
				.isInstanceOf(V2ApiException.class)
				.hasFieldOrPropertyWithValue("code", "SUBSCRIPTION_VERSION_MISMATCH");
	}

	@Test
	void changePlanKeepsCurrentCycleAndCreatesPendingSnapshot() {
		long subscriptionId = createSubscription("change-plan");

		V2SubscriptionService.V2Result result = service.command(
				member.getId(), subscriptionId, "change-plan", "change-plan", "\"0\"", Map.of("planVersionId", planVersionId));

		assertThat(result.etag()).isEqualTo("\"1\"");
		assertThat(result.body()).containsEntry("version", 1L);
		assertThat(result.body().get("pendingSnapshot")).isNotNull();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pending_plan_changes WHERE subscription_id=?", Integer.class, subscriptionId)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT ss.delivery_cycle_weeks FROM pending_plan_changes p JOIN subscription_snapshots ss ON ss.id=p.snapshot_id WHERE p.subscription_id=?", Integer.class, subscriptionId)).isEqualTo(4);
	}

	@Test
	void skipNextMarksCurrentScheduleAndCreatesReplacement() {
		long subscriptionId = createSubscription("skip-next");

		V2SubscriptionService.V2Result result = service.command(member.getId(), subscriptionId, "skip-next", "skip-next", "\"0\"", Map.of());

		assertThat(result.etag()).isEqualTo("\"1\"");
		assertThat(jdbc.queryForObject("SELECT version FROM subscriptions WHERE id=?", Long.class, subscriptionId)).isEqualTo(1L);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND status='SKIPPED'", Integer.class, subscriptionId)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND status='SCHEDULED'", Integer.class, subscriptionId)).isEqualTo(1);
	}

	@Test
	void pauseReplayAndResumePreserveVersionContract() {
		long subscriptionId = createSubscription("pause-resume");

		V2SubscriptionService.V2Result paused = service.command(member.getId(), subscriptionId, "pause", "pause-replay", "\"0\"", Map.of());
		V2SubscriptionService.V2Result replay = service.command(member.getId(), subscriptionId, "pause", "pause-replay", null, Map.of());

		assertThat(paused.etag()).isEqualTo("\"1\"");
		assertThat(paused.body()).containsEntry("status", "PAUSED").containsEntry("version", 1L);
		assertThat(replay.replay()).isTrue();
		assertThat(replay.etag()).isEqualTo("\"1\"");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND status='HELD'", Integer.class, subscriptionId)).isEqualTo(1);

		V2SubscriptionService.V2Result resumed = service.command(member.getId(), subscriptionId, "resume", "resume-after-pause", "\"1\"", Map.of());

		assertThat(resumed.etag()).isEqualTo("\"2\"");
		assertThat(resumed.body()).containsEntry("status", "ACTIVE").containsEntry("version", 2L);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND status='SCHEDULED'", Integer.class, subscriptionId)).isEqualTo(1);
	}

	@Test
	void cancelRemovesPendingChangeAndCancelsFutureSchedules() {
		long subscriptionId = createSubscription("cancel");
		service.command(member.getId(), subscriptionId, "change-plan", "change-before-cancel", "\"0\"", Map.of("planVersionId", planVersionId));

		V2SubscriptionService.V2Result canceled = service.command(member.getId(), subscriptionId, "cancel", "cancel", "\"1\"", Map.of());

		assertThat(canceled.etag()).isEqualTo("\"2\"");
		assertThat(canceled.body()).containsEntry("status", "CANCELED").containsEntry("version", 2L);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pending_plan_changes WHERE subscription_id=?", Integer.class, subscriptionId)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND status='CANCELED'", Integer.class, subscriptionId)).isEqualTo(1);
	}

	@Test
	void commandNormalizationIsIndependentOfDefaultLocale() {
		long subscriptionId = createSubscription("locale");
		Locale previous = Locale.getDefault();

		try {
			Locale.setDefault(Locale.forLanguageTag("tr-TR"));
			V2SubscriptionService.V2Result result = service.command(member.getId(), subscriptionId, "skip-next", "locale-skip", "\"0\"", Map.of());
			assertThat(result.etag()).isEqualTo("\"1\"");
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND status='SKIPPED'", Integer.class, subscriptionId)).isEqualTo(1);
		} finally {
			Locale.setDefault(previous);
		}
	}

	private long createSubscription(String key) {
		long petId = ((Number) service.createPet(member.getId(), Map.of("name", "반려동물-" + key, "petType", "DOG")).get("petId")).longValue();
		V2SubscriptionService.V2Result created = service.createSubscription(
				member.getId(),
				"create-" + key,
				Map.of("petId", petId, "planVersionId", planVersionId, "deliveryCycleWeeks", 4));
		return ((Number) created.body().get("subscriptionId")).longValue();
	}
}
