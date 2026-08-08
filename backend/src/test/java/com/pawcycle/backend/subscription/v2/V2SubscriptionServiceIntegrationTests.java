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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class V2SubscriptionServiceIntegrationTests {

	@Autowired private V2SubscriptionService service;
	@Autowired private LegacyMvp2MigrationService legacyMigration;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private MemberRepository members;
	@Autowired private ProductRepository products;
	@Autowired private SkuRepository skus;
	@Autowired private PasswordEncoder passwordEncoder;
	private Member member;
	private Sku sku;
	private long planVersionId;

	@BeforeEach
	void setUp() {
		member = members.saveAndFlush(new Member("v2-" + UUID.randomUUID() + "@example.test", passwordEncoder.encode("test-password")));
		Product product = products.saveAndFlush(new Product("V2 plan product", "test", null, "DOG", null, "PUBLIC"));
		sku = skus.saveAndFlush(new Sku(product, "v2-sku-" + UUID.randomUUID(), new BigDecimal("12000.00"), true, 1));
		jdbc.update("INSERT INTO subscription_plans(name,target_pet_type,on_sale) VALUES (?,?,true)", "DOG starter", "DOG");
		long planId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update("INSERT INTO plan_versions(plan_id,package_price_krw,is_migration_only) VALUES (?,24000,false)", planId);
		planVersionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update("INSERT INTO plan_items(plan_version_id,sku_id,quantity) VALUES (?,?,2)", planVersionId, sku.getId());
		jdbc.update("INSERT INTO plan_version_delivery_cycles(plan_version_id,delivery_cycle_weeks) VALUES (?,4)", planVersionId);
		jdbc.update("UPDATE subscription_plans SET current_plan_version_id=? WHERE id=?", planVersionId, planId);
	}

	@Test
	@SuppressWarnings("unchecked")
	void listPagesKeepRelatedRowsOrderingAndMemberIsolationAfterBatchAssembly() {
		long petId = ((Number) service.createPet(member.getId(), Map.of("name", "보리", "petType", "DOG")).get("petId")).longValue();
		jdbc.update("INSERT INTO subscription_plans(name,target_pet_type,on_sale) VALUES (?,?,true)", "DOG multi", "DOG");
		long multiPlanId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update("INSERT INTO plan_versions(plan_id,package_price_krw,is_migration_only) VALUES (?,24000,false)", multiPlanId);
		long multiVersionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update("INSERT INTO plan_items(plan_version_id,sku_id,quantity) VALUES (?,?,?)", multiVersionId, sku.getId(), 3);
		jdbc.update("INSERT INTO plan_version_delivery_cycles(plan_version_id,delivery_cycle_weeks) VALUES (?,?)", multiVersionId, 2);
		jdbc.update("INSERT INTO plan_version_delivery_cycles(plan_version_id,delivery_cycle_weeks) VALUES (?,?)", multiVersionId, 8);
		jdbc.update("UPDATE subscription_plans SET current_plan_version_id=? WHERE id=?", multiVersionId, multiPlanId);
		jdbc.update("INSERT INTO subscription_plans(name,target_pet_type,on_sale) VALUES (?,?,true)", "DOG empty", "DOG");
		long emptyPlanId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update("INSERT INTO plan_versions(plan_id,package_price_krw,is_migration_only) VALUES (?,24000,false)", emptyPlanId);
		long emptyVersionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update("UPDATE subscription_plans SET current_plan_version_id=? WHERE id=?", emptyVersionId, emptyPlanId);

		List<Map<String, Object>> plans = (List<Map<String, Object>>) service.plans(member.getId(), petId, 0, 100).get("items");
		assertThat(plans).extracting(plan -> ((Number) plan.get("planId")).longValue()).isSorted();
		assertThat(plans).filteredOn(plan -> "DOG multi".equals(plan.get("planName"))).singleElement().satisfies(plan -> {
			assertThat(plan.get("items")).isEqualTo(List.of(Map.of("skuId", sku.getId(), "quantity", 3)));
			assertThat(plan.get("allowedDeliveryCycleWeeks")).isEqualTo(List.of(2, 8));
		});
		assertThat(plans).filteredOn(plan -> "DOG empty".equals(plan.get("planName"))).singleElement().satisfies(plan -> {
			assertThat(plan.get("items")).isEqualTo(List.of());
			assertThat(plan.get("allowedDeliveryCycleWeeks")).isEqualTo(List.of());
		});
		List<Map<String, Object>> secondPlanPage = (List<Map<String, Object>>) service.plans(member.getId(), petId, 1, 1).get("items");
		assertThat(secondPlanPage).containsExactly(plans.get(1));

		long firstSubscriptionId = ((Number) service.createSubscription(member.getId(), "list-first", Map.of("petId", petId, "planVersionId", planVersionId, "deliveryCycleWeeks", 4)).body().get("subscriptionId")).longValue();
		long secondSubscriptionId = ((Number) service.createSubscription(member.getId(), "list-second", Map.of("petId", petId, "planVersionId", multiVersionId, "deliveryCycleWeeks", 2)).body().get("subscriptionId")).longValue();
		Sku secondSku = skus.saveAndFlush(new Sku(sku.getProduct(), "v2-list-sku-" + UUID.randomUUID(), new BigDecimal("13000.00"), true, 1));
		long secondSnapshotId = jdbc.queryForObject("SELECT current_snapshot_id FROM subscriptions WHERE id=?", Long.class, secondSubscriptionId);
		jdbc.update("INSERT INTO subscription_snapshot_items(snapshot_id,sku_id,quantity) VALUES (?,?,?)", secondSnapshotId, secondSku.getId(), 1);
		long firstSnapshotId = jdbc.queryForObject("SELECT current_snapshot_id FROM subscriptions WHERE id=?", Long.class, firstSubscriptionId);
		jdbc.update("DELETE FROM subscription_snapshot_items WHERE snapshot_id=?", firstSnapshotId);
		jdbc.update("INSERT INTO subscription_schedules(subscription_id,scheduled_date,status) VALUES (?,?,'SCHEDULED')", secondSubscriptionId, LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1));
		Member other = members.saveAndFlush(new Member("v2-other-" + UUID.randomUUID() + "@example.test", passwordEncoder.encode("test-password")));
		long otherPetId = ((Number) service.createPet(other.getId(), Map.of("name", "다른 회원", "petType", "DOG")).get("petId")).longValue();
		service.createSubscription(other.getId(), "list-other", Map.of("petId", otherPetId, "planVersionId", planVersionId, "deliveryCycleWeeks", 4));

		List<Map<String, Object>> subscriptions = (List<Map<String, Object>>) service.subscriptions(member.getId(), 0, 100).get("items");
		assertThat(subscriptions).extracting(subscription -> ((Number) subscription.get("subscriptionId")).longValue()).containsExactly(secondSubscriptionId, firstSubscriptionId);
		Map<String, Object> second = subscriptions.getFirst();
		assertThat(second.get("pet")).isEqualTo(Map.of("petId", petId, "name", "보리", "petType", "DOG"));
		assertThat(((Map<String, Object>) second.get("currentSnapshot")).get("items")).isEqualTo(List.of(Map.of("skuId", sku.getId(), "quantity", 3), Map.of("skuId", secondSku.getId(), "quantity", 1)));
		assertThat(second.get("nextScheduledDate")).isEqualTo(LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1));
		assertThat(((Map<String, Object>) subscriptions.get(1).get("currentSnapshot")).get("items")).isEqualTo(List.of());
		List<Map<String, Object>> secondSubscriptionPage = (List<Map<String, Object>>) service.subscriptions(member.getId(), 1, 1).get("items");
		assertThat(secondSubscriptionPage).containsExactly(subscriptions.get(1));
	}

	@Test
	void mysqlPersistsReplayAndRejectsMismatchedPayload() {
		long petId = ((Number) service.createPet(member.getId(), Map.of("name", "보리", "petType", "DOG")).get("petId")).longValue();
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("deliveryCycleWeeks", 4);
		request.put("planVersionId", planVersionId);
		request.put("petId", petId);
		V2SubscriptionService.V2Result created = service.createSubscription(member.getId(), "create-replay-key", request);
		assertThat(jdbc.update("UPDATE subscription_creation_idempotency_results SET response_body=JSON_SET(response_body,'$.currentSnapshot.snapshotId',9001) WHERE member_id=? AND idempotency_key=?", member.getId(), "create-replay-key")).isEqualTo(1);
		V2SubscriptionService.V2Result replay = service.createSubscription(member.getId(), "create-replay-key", Map.of("petId", petId, "planVersionId", planVersionId, "deliveryCycleWeeks", 4));

		assertThat(created.status()).isEqualTo(201);
		assertThat(created.body()).containsKeys("pet", "currentSnapshot", "schedules", "commandHistory");
		assertThat(((Map<?, ?>) created.body().get("currentSnapshot")).containsKey("snapshotId")).isFalse();
		assertThat(replay.replay()).isTrue();
		assertThat(((Map<?, ?>) replay.body().get("currentSnapshot")).containsKey("snapshotId")).isFalse();
		assertThat(jdbc.queryForObject("SELECT response_body FROM subscription_creation_idempotency_results WHERE member_id=? AND idempotency_key=?", String.class, member.getId(), "create-replay-key")).doesNotContain("\"snapshotId\"");
		assertThatThrownBy(() -> service.createSubscription(member.getId(), "create-replay-key", Map.of("petId", petId, "planVersionId", planVersionId, "deliveryCycleWeeks", 2)))
				.isInstanceOf(V2ApiException.class).hasFieldOrPropertyWithValue("code", "IDEMPOTENCY_KEY_REUSED");
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void legacyWhitespacePetTypeNormalizesHidesV1AndKeepsFutureReconciliation() {
		jdbc.update("UPDATE products SET pet_type=' DOG ' WHERE id=?", sku.getProduct().getId());
		jdbc.update("INSERT INTO subscriptions(member_id,sku_id,quantity,delivery_cycle_weeks,created_date,next_order_date) VALUES (?,?,?,?,?,?)", member.getId(), sku.getId(), 1, 4, LocalDate.now(ZoneId.of("Asia/Seoul")).minusWeeks(4), LocalDate.now(ZoneId.of("Asia/Seoul")).plusWeeks(4));
		long subscriptionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		assertThat(legacyMigration.preflight().valid()).isTrue();
		legacyMigration.migrateAfterSourceWriteFreeze(true);
		assertThat(jdbc.queryForObject("SELECT legacy_api_visible FROM subscriptions WHERE id=?", Boolean.class, subscriptionId)).isFalse();
		assertThat(jdbc.queryForObject("SELECT target_pet_type FROM subscription_plans WHERE name IS NULL ORDER BY id DESC LIMIT 1", String.class)).isEqualTo("DOG");
		assertThat(jdbc.queryForObject("SELECT effective_snapshot_id FROM subscription_schedules WHERE subscription_id=?", Long.class, subscriptionId)).isNull();

		jdbc.update(
				"UPDATE subscription_schedules SET scheduled_date=? WHERE subscription_id=?",
				LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1),
				subscriptionId);

		service.reconcileActiveSubscriptions();

		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND effective_snapshot_id IS NOT NULL",
				Integer.class,
				subscriptionId)).isEqualTo(1);

		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND status='SCHEDULED' AND scheduled_date>?",
				Integer.class,
				subscriptionId,
				LocalDate.now(ZoneId.of("Asia/Seoul")))).isEqualTo(1);
	}
}
