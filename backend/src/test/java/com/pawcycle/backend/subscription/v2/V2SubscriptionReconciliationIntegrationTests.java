package com.pawcycle.backend.subscription.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.infra.SkuRepository;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.infra.MemberRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class V2SubscriptionReconciliationIntegrationTests {
	private static final String EMAIL_PREFIX = "ops-recon-001-";
	private static final String PRODUCT_PREFIX = "OPS-RECON-001 product ";
	private static final String PLAN_PREFIX = "OPS-RECON-001 plan ";
	private static final String EFFECTIVE_SNAPSHOT_UPDATE =
			"UPDATE subscription_schedules SET effective_snapshot_id=? WHERE id=? AND effective_snapshot_id IS NULL";
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	@Autowired private V2SubscriptionService service;
	@MockitoSpyBean private JdbcTemplate jdbc;
	@Autowired private MemberRepository members;
	@Autowired private ProductRepository products;
	@Autowired private SkuRepository skus;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private MeterRegistry meterRegistry;

	private Member member;
	private long planVersionId;

	@BeforeEach
	void setUp() {
		cleanFixtures();
		String suffix = UUID.randomUUID().toString();
		member = members.saveAndFlush(new Member(
				EMAIL_PREFIX + suffix + "@example.test",
				passwordEncoder.encode("test-password")));
		Product product = products.saveAndFlush(new Product(
				PRODUCT_PREFIX + suffix, "test", null, "DOG", null, "PUBLIC"));
		Sku sku = skus.saveAndFlush(new Sku(
				product, "ops-recon-001-sku-" + suffix, new BigDecimal("12000.00"), true, 1));
		jdbc.update("INSERT INTO subscription_plans(name,target_pet_type,on_sale) VALUES (?,?,true)", PLAN_PREFIX + suffix, "DOG");
		long planId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update("INSERT INTO plan_versions(plan_id,package_price_krw,is_migration_only) VALUES (?,24000,false)", planId);
		planVersionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update("INSERT INTO plan_items(plan_version_id,sku_id,quantity) VALUES (?,?,2)", planVersionId, sku.getId());
		jdbc.update("INSERT INTO plan_version_delivery_cycles(plan_version_id,delivery_cycle_weeks) VALUES (?,4)", planVersionId);
		jdbc.update("UPDATE subscription_plans SET current_plan_version_id=? WHERE id=?", planVersionId, planId);
	}

	@AfterEach
	void tearDown() {
		cleanFixtures();
	}

	@Test
	void failedSubscriptionRollsBackAndNextSubscriptionCommits(CapturedOutput output) {
		double executionsBefore = meterRegistry.get(
				"pawcycle.subscription.reconciliation.executions").counter().count();
		double processedBefore = meterRegistry.get(
				"pawcycle.subscription.reconciliation.processed").counter().count();
		double failuresBefore = meterRegistry.get(
				"pawcycle.subscription.reconciliation.failures").counter().count();
		long durationCountBefore = meterRegistry.get(
				"pawcycle.subscription.reconciliation.duration").timer().count();
		long petId = ((Number) service.createPet(
				member.getId(), Map.of("name", "보리", "petType", "DOG")).get("petId")).longValue();
		long failedSubscriptionId = createSubscription(petId, "failed");
		long successfulSubscriptionId = createSubscription(petId, "successful");
		long originalFailedSnapshotId = jdbc.queryForObject(
				"SELECT current_snapshot_id FROM subscriptions WHERE id=?", Long.class, failedSubscriptionId);
		service.command(member.getId(), failedSubscriptionId, "change-plan", "failed-change", "\"0\"",
				Map.of("planVersionId", planVersionId));
		long pendingFailedSnapshotId = jdbc.queryForObject(
				"SELECT snapshot_id FROM pending_plan_changes WHERE subscription_id=?",
				Long.class, failedSubscriptionId);
		LocalDate yesterday = LocalDate.now(SEOUL).minusDays(1);
		jdbc.update("UPDATE subscription_schedules SET scheduled_date=? WHERE subscription_id IN (?,?)",
				yesterday, failedSubscriptionId, successfulSubscriptionId);
		long failedScheduleId = jdbc.queryForObject(
				"SELECT id FROM subscription_schedules WHERE subscription_id=?", Long.class, failedSubscriptionId);
		doAnswer(invocation -> {
			int updated = (int) invocation.callRealMethod();
			if (invocation.getArgument(2, Number.class).longValue() == failedScheduleId) {
				throw new IllegalStateException("OPS-RECON-001 intentional failure");
			}
			return updated;
		}).when(jdbc).update(eq(EFFECTIVE_SNAPSHOT_UPDATE), any(Object[].class));
		long activeSubscriptions = jdbc.queryForObject(
				"SELECT COUNT(*) FROM subscriptions WHERE mvp2_managed=true AND status='ACTIVE'",
				Long.class);

		service.reconcileActiveSubscriptions();

		assertThat(jdbc.queryForObject(
				"SELECT version FROM subscriptions WHERE id=?", Long.class, failedSubscriptionId)).isEqualTo(1L);
		assertThat(jdbc.queryForObject(
				"SELECT current_snapshot_id FROM subscriptions WHERE id=?", Long.class, failedSubscriptionId))
				.isEqualTo(originalFailedSnapshotId);
		assertThat(jdbc.queryForObject(
				"SELECT snapshot_id FROM pending_plan_changes WHERE subscription_id=?",
				Long.class, failedSubscriptionId)).isEqualTo(pendingFailedSnapshotId);
		assertThat(jdbc.queryForObject(
				"SELECT effective_snapshot_id FROM subscription_schedules WHERE subscription_id=? AND scheduled_date=?",
				Long.class, failedSubscriptionId, yesterday)).isNull();
		assertThat(jdbc.queryForObject(
				"SELECT version FROM subscriptions WHERE id=?", Long.class, successfulSubscriptionId)).isEqualTo(1L);
		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND effective_snapshot_id IS NOT NULL",
				Integer.class, successfulSubscriptionId)).isEqualTo(1);
		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND status='SCHEDULED' AND scheduled_date>?",
				Integer.class, successfulSubscriptionId, LocalDate.now(SEOUL))).isEqualTo(1);
		assertThat(output).contains(
				"Subscription reconciliation failed; subscriptionId=" + failedSubscriptionId,
				"OPS-RECON-001 intentional failure");
		assertThat(meterRegistry.get("pawcycle.subscription.reconciliation.executions").counter().count())
				.isEqualTo(executionsBefore + 1);
		assertThat(meterRegistry.get("pawcycle.subscription.reconciliation.processed").counter().count())
				.isEqualTo(processedBefore + activeSubscriptions);
		assertThat(meterRegistry.get("pawcycle.subscription.reconciliation.failures").counter().count())
				.isEqualTo(failuresBefore + 1);
		assertThat(meterRegistry.get("pawcycle.subscription.reconciliation.duration").timer().count())
				.isEqualTo(durationCountBefore + 1);

		service.reconcileActiveSubscriptions();

		assertThat(jdbc.queryForObject(
				"SELECT version FROM subscriptions WHERE id=?", Long.class, successfulSubscriptionId)).isEqualTo(1L);
	}

	private long createSubscription(long petId, String idempotencyKey) {
		V2SubscriptionService.V2Result result = service.createSubscription(
				member.getId(), idempotencyKey,
				Map.of("petId", petId, "planVersionId", planVersionId, "deliveryCycleWeeks", 4));
		return ((Number) result.body().get("subscriptionId")).longValue();
	}

	private void cleanFixtures() {
		String memberFilter = "SELECT id FROM members WHERE email LIKE '" + EMAIL_PREFIX + "%@example.test'";
		jdbc.update("DELETE p FROM pending_plan_changes p JOIN subscriptions s ON s.id=p.subscription_id WHERE s.member_id IN (" + memberFilter + ")");
		jdbc.update("DELETE r FROM subscription_command_idempotency_results r JOIN subscriptions s ON s.id=r.subscription_id WHERE s.member_id IN (" + memberFilter + ")");
		jdbc.update("DELETE FROM subscription_creation_idempotency_results WHERE member_id IN (" + memberFilter + ")");
		jdbc.update("DELETE h FROM subscription_command_history h JOIN subscriptions s ON s.id=h.subscription_id WHERE s.member_id IN (" + memberFilter + ")");
		jdbc.update("DELETE sc FROM subscription_schedules sc JOIN subscriptions s ON s.id=sc.subscription_id WHERE s.member_id IN (" + memberFilter + ")");
		jdbc.update("DELETE si FROM subscription_snapshot_items si JOIN subscription_snapshots ss ON ss.id=si.snapshot_id JOIN subscriptions s ON s.id=ss.subscription_id WHERE s.member_id IN (" + memberFilter + ")");
		jdbc.update("UPDATE subscriptions SET current_snapshot_id=NULL WHERE member_id IN (" + memberFilter + ")");
		jdbc.update("DELETE ss FROM subscription_snapshots ss JOIN subscriptions s ON s.id=ss.subscription_id WHERE s.member_id IN (" + memberFilter + ")");
		jdbc.update("DELETE FROM subscriptions WHERE member_id IN (" + memberFilter + ")");
		jdbc.update("DELETE FROM pets WHERE member_id IN (" + memberFilter + ")");
		jdbc.update("UPDATE subscription_plans SET current_plan_version_id=NULL WHERE name LIKE ?", PLAN_PREFIX + "%");
		jdbc.update("DELETE c FROM plan_version_delivery_cycles c JOIN plan_versions v ON v.id=c.plan_version_id JOIN subscription_plans p ON p.id=v.plan_id WHERE p.name LIKE ?", PLAN_PREFIX + "%");
		jdbc.update("DELETE i FROM plan_items i JOIN plan_versions v ON v.id=i.plan_version_id JOIN subscription_plans p ON p.id=v.plan_id WHERE p.name LIKE ?", PLAN_PREFIX + "%");
		jdbc.update("DELETE v FROM plan_versions v JOIN subscription_plans p ON p.id=v.plan_id WHERE p.name LIKE ?", PLAN_PREFIX + "%");
		jdbc.update("DELETE FROM subscription_plans WHERE name LIKE ?", PLAN_PREFIX + "%");
		jdbc.update("DELETE s FROM skus s JOIN products p ON p.id=s.product_id WHERE p.name LIKE ?", PRODUCT_PREFIX + "%");
		jdbc.update("DELETE FROM products WHERE name LIKE ?", PRODUCT_PREFIX + "%");
		jdbc.update("DELETE FROM members WHERE email LIKE ?", EMAIL_PREFIX + "%@example.test");
	}
}
