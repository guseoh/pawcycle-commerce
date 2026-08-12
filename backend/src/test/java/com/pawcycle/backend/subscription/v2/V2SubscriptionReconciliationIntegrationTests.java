package com.pawcycle.backend.subscription.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.infra.SkuRepository;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.infra.MemberRepository;
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
	private static final String INSERT_FUTURE_SCHEDULE =
			"INSERT INTO subscription_schedules(subscription_id,scheduled_date,status,effective_snapshot_id) "
					+ "VALUES (?,?,'SCHEDULED',NULL)";
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	@Autowired private V2SubscriptionService service;
	@Autowired private SubscriptionOrderAutomationService automation;
	@MockitoSpyBean private JdbcTemplate jdbc;
	@Autowired private MemberRepository members;
	@Autowired private ProductRepository products;
	@Autowired private SkuRepository skus;
	@Autowired private PasswordEncoder passwordEncoder;

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
		Sku sku = skus.saveAndFlush(com.pawcycle.backend.support.TestSkuFactory.sku(
				product, "ops-recon-001-sku-" + suffix, new BigDecimal("12000.00"), true, 1));
		jdbc.update(
				"INSERT INTO subscription_plans(name,target_pet_type,on_sale) VALUES (?,?,true)",
				PLAN_PREFIX + suffix,
				"DOG");
		long planId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update(
				"INSERT INTO plan_versions(plan_id,package_price_krw,is_migration_only) "
						+ "VALUES (?,24000,false)",
				planId);
		planVersionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update(
				"INSERT INTO plan_items(plan_version_id,sku_id,quantity) VALUES (?,?,2)",
				planVersionId,
				sku.getId());
		jdbc.update(
				"INSERT INTO plan_version_delivery_cycles(plan_version_id,delivery_cycle_weeks) VALUES (?,4)",
				planVersionId);
		jdbc.update(
				"UPDATE subscription_plans SET current_plan_version_id=? WHERE id=?",
				planVersionId,
				planId);
	}

	@AfterEach
	void tearDown() {
		reset(jdbc);
		cleanFixtures();
	}

	@Test
	void reconciliationNeverConsumesDueScheduleWithoutOrder() {
		long subscriptionId = createSubscription("due-unprocessed");
		service.command(
				member.getId(),
				subscriptionId,
				"change-plan",
				"pending-due",
				"\"0\"",
				Map.of("planVersionId", planVersionId));
		long originalSnapshotId = jdbc.queryForObject(
				"SELECT current_snapshot_id FROM subscriptions WHERE id=?",
				Long.class,
				subscriptionId);
		long pendingSnapshotId = jdbc.queryForObject(
				"SELECT snapshot_id FROM pending_plan_changes WHERE subscription_id=?",
				Long.class,
				subscriptionId);
		LocalDate yesterday = LocalDate.now(SEOUL).minusDays(1);
		jdbc.update(
				"UPDATE subscription_schedules SET scheduled_date=? WHERE subscription_id=?",
				yesterday,
				subscriptionId);

		service.reconcileActiveSubscriptions();

		assertThat(jdbc.queryForObject(
				"SELECT version FROM subscriptions WHERE id=?", Long.class, subscriptionId)).isEqualTo(1L);
		assertThat(jdbc.queryForObject(
				"SELECT current_snapshot_id FROM subscriptions WHERE id=?",
				Long.class,
				subscriptionId)).isEqualTo(originalSnapshotId);
		assertThat(jdbc.queryForObject(
				"SELECT snapshot_id FROM pending_plan_changes WHERE subscription_id=?",
				Long.class,
				subscriptionId)).isEqualTo(pendingSnapshotId);
		assertThat(jdbc.queryForObject(
				"SELECT effective_snapshot_id FROM subscription_schedules WHERE subscription_id=?",
				Long.class,
				subscriptionId)).isNull();
		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM subscription_orders WHERE subscription_id=?",
				Integer.class,
				subscriptionId)).isZero();
		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND scheduled_date>?",
				Integer.class,
				subscriptionId,
				LocalDate.now(SEOUL))).isZero();
	}

	@Test
	void failedProcessedResultRepairRollsBackAndNextSubscriptionCommits(CapturedOutput output) {
		long failedSubscriptionId = createProcessedSubscription("failed-repair");
		long successfulSubscriptionId = createProcessedSubscription("successful-repair");
		doAnswer(invocation -> {
			int updated = (int) invocation.callRealMethod();
			if (invocation.getArgument(1, Number.class).longValue() == failedSubscriptionId) {
				throw new IllegalStateException("intentional reconciliation repair failure");
			}
			return updated;
		}).when(jdbc).update(eq(INSERT_FUTURE_SCHEDULE), any(Object[].class));

		service.reconcileActiveSubscriptions();

		assertThat(futureScheduleCount(failedSubscriptionId)).isZero();
		assertThat(jdbc.queryForObject(
				"SELECT version FROM subscriptions WHERE id=?",
				Long.class,
				failedSubscriptionId)).isEqualTo(1L);
		assertThat(futureScheduleCount(successfulSubscriptionId)).isEqualTo(1);
		assertThat(jdbc.queryForObject(
				"SELECT version FROM subscriptions WHERE id=?",
				Long.class,
				successfulSubscriptionId)).isEqualTo(2L);
		assertThat(output).contains(
				"Subscription reconciliation failed; subscriptionId=" + failedSubscriptionId,
				"intentional reconciliation repair failure");

		reset(jdbc);
		service.reconcileActiveSubscriptions();

		assertThat(futureScheduleCount(failedSubscriptionId)).isEqualTo(1);
		assertThat(jdbc.queryForObject(
				"SELECT version FROM subscriptions WHERE id=?",
				Long.class,
				failedSubscriptionId)).isEqualTo(2L);
	}

	private long createProcessedSubscription(String key) {
		long subscriptionId = createSubscription(key);
		jdbc.update(
				"UPDATE subscription_schedules SET scheduled_date=? WHERE subscription_id=?",
				LocalDate.now(SEOUL),
				subscriptionId);
		assertThat(automation.processDueSchedules(10).ordersCreated()).isEqualTo(1);
		jdbc.update(
				"DELETE FROM subscription_schedules WHERE subscription_id=? AND scheduled_date>?",
				subscriptionId,
				LocalDate.now(SEOUL));
		return subscriptionId;
	}

	private int futureScheduleCount(long subscriptionId) {
		return jdbc.queryForObject(
				"SELECT COUNT(*) FROM subscription_schedules "
						+ "WHERE subscription_id=? AND status='SCHEDULED' AND scheduled_date>?",
				Integer.class,
				subscriptionId,
				LocalDate.now(SEOUL));
	}

	private long createSubscription(String idempotencyKey) {
		long petId = ((Number) service.createPet(
				member.getId(), Map.of("name", "보리", "petType", "DOG")).get("petId"))
				.longValue();
		V2SubscriptionService.V2Result result = service.createSubscription(
				member.getId(),
				idempotencyKey,
				Map.of("petId", petId, "planVersionId", planVersionId, "deliveryCycleWeeks", 4));
		return ((Number) result.body().get("subscriptionId")).longValue();
	}

	private void cleanFixtures() {
		String memberFilter = "SELECT id FROM members WHERE email LIKE '" + EMAIL_PREFIX + "%@example.test'";
		jdbc.update("DELETE p FROM pending_plan_changes p JOIN subscriptions s ON s.id=p.subscription_id WHERE s.member_id IN (" + memberFilter + ")");
		jdbc.update("DELETE r FROM subscription_command_idempotency_results r JOIN subscriptions s ON s.id=r.subscription_id WHERE s.member_id IN (" + memberFilter + ")");
		jdbc.update("DELETE FROM subscription_creation_idempotency_results WHERE member_id IN (" + memberFilter + ")");
		jdbc.update("DELETE h FROM subscription_command_history h JOIN subscriptions s ON s.id=h.subscription_id WHERE s.member_id IN (" + memberFilter + ")");
		jdbc.update("DELETE item FROM subscription_order_items item JOIN subscription_orders orders ON orders.id=item.order_id JOIN subscriptions s ON s.id=orders.subscription_id WHERE s.member_id IN (" + memberFilter + ")");
		jdbc.update("DELETE orders FROM subscription_orders orders JOIN subscriptions s ON s.id=orders.subscription_id WHERE s.member_id IN (" + memberFilter + ")");
		jdbc.update("DELETE schedule FROM subscription_schedules schedule JOIN subscriptions s ON s.id=schedule.subscription_id WHERE s.member_id IN (" + memberFilter + ")");
		jdbc.update("DELETE item FROM subscription_snapshot_items item JOIN subscription_snapshots snapshot ON snapshot.id=item.snapshot_id JOIN subscriptions s ON s.id=snapshot.subscription_id WHERE s.member_id IN (" + memberFilter + ")");
		jdbc.update("UPDATE subscriptions SET current_snapshot_id=NULL WHERE member_id IN (" + memberFilter + ")");
		jdbc.update("DELETE snapshot FROM subscription_snapshots snapshot JOIN subscriptions s ON s.id=snapshot.subscription_id WHERE s.member_id IN (" + memberFilter + ")");
		jdbc.update("DELETE FROM subscriptions WHERE member_id IN (" + memberFilter + ")");
		jdbc.update("DELETE FROM pets WHERE member_id IN (" + memberFilter + ")");
		jdbc.update("UPDATE subscription_plans SET current_plan_version_id=NULL WHERE name LIKE ?", PLAN_PREFIX + "%");
		jdbc.update("DELETE cycle FROM plan_version_delivery_cycles cycle JOIN plan_versions version ON version.id=cycle.plan_version_id JOIN subscription_plans plan ON plan.id=version.plan_id WHERE plan.name LIKE ?", PLAN_PREFIX + "%");
		jdbc.update("DELETE item FROM plan_items item JOIN plan_versions version ON version.id=item.plan_version_id JOIN subscription_plans plan ON plan.id=version.plan_id WHERE plan.name LIKE ?", PLAN_PREFIX + "%");
		jdbc.update("DELETE version FROM plan_versions version JOIN subscription_plans plan ON plan.id=version.plan_id WHERE plan.name LIKE ?", PLAN_PREFIX + "%");
		jdbc.update("DELETE FROM subscription_plans WHERE name LIKE ?", PLAN_PREFIX + "%");
		jdbc.update("DELETE sku FROM skus sku JOIN products product ON product.id=sku.product_id WHERE product.name LIKE ?", PRODUCT_PREFIX + "%");
		jdbc.update("DELETE FROM products WHERE name LIKE ?", PRODUCT_PREFIX + "%");
		jdbc.update("DELETE FROM members WHERE email LIKE ?", EMAIL_PREFIX + "%@example.test");
	}
}
