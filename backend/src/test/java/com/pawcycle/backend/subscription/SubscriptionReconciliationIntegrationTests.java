package com.pawcycle.backend.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.persistence.CategoryRepository;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.persistence.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.persistence.SkuRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.persistence.MemberRepository;
import com.pawcycle.backend.subscription.api.CreatePetRequest;
import com.pawcycle.backend.subscription.api.CreateSubscriptionRequest;
import com.pawcycle.backend.subscription.api.SubscriptionCommandRequest;
import com.pawcycle.backend.support.TransactionalTestSql;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class SubscriptionReconciliationIntegrationTests {

  private static final String EMAIL_PREFIX = "ops-recon-001-";
  private static final String PRODUCT_PREFIX = "OPS-RECON-001 product ";
  private static final String PLAN_PREFIX = "OPS-RECON-001 plan ";
  private static final String INSERT_FUTURE_SCHEDULE =
      "INSERT INTO"
          + " subscription_schedules(subscription_id,scheduled_date,status,effective_snapshot_id)"
          + " VALUES (?,?,'SCHEDULED',NULL)";
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  @Autowired private SubscriptionService service;
  @Autowired private SubscriptionOrderAutomationService automation;
  @MockitoSpyBean private JdbcTemplate nativeJdbc;
  @Autowired private MemberRepository members;
  @Autowired private ProductRepository products;
  @Autowired private SkuRepository skus;
  @Autowired private CategoryRepository categories;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private PlatformTransactionManager transactionManager;

  private TransactionalTestSql jdbc;
  private Member member;
  private long planVersionId;

  @BeforeEach
  void setUp() {
    jdbc = new TransactionalTestSql(nativeJdbc, transactionManager);
    cleanFixtures();
    String suffix = UUID.randomUUID().toString();
    member =
        members.saveAndFlush(
            new Member(
                EMAIL_PREFIX + suffix + "@example.test", passwordEncoder.encode("test-password")));
    Product product =
        new Product(activeCategory(suffix), PRODUCT_PREFIX + suffix, "test", null, "DOG", null);
    product.transitionTo(com.pawcycle.backend.catalog.product.domain.ProductStatus.PUBLIC);
    product = products.saveAndFlush(product);
    Sku sku =
        skus.saveAndFlush(
            com.pawcycle.backend.support.TestSkuFactory.sku(
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
        "INSERT INTO plan_version_delivery_cycles(plan_version_id,delivery_cycle_weeks) VALUES"
            + " (?,4)",
        planVersionId);
    jdbc.update(
        "UPDATE subscription_plans SET current_plan_version_id=? WHERE id=?",
        planVersionId,
        planId);
    enableCommerceFulfillment();
  }

  private Category activeCategory(String suffix) {
    return categories.saveAndFlush(
        new Category("ops-recon-" + suffix, "ops-recon-" + suffix, 0, true));
  }

  private void enableCommerceFulfillment() {
    jdbc.update(
        "INSERT INTO inventories(sku_id,available_quantity,reserved_quantity,version) SELECT"
            + " sku.id,100,0,0 FROM skus sku JOIN products product ON product.id=sku.product_id"
            + " WHERE product.name LIKE ? ON DUPLICATE KEY UPDATE available_quantity=100",
        PRODUCT_PREFIX + "%");
    jdbc.update(
        "INSERT INTO"
            + " member_addresses(member_id,name,recipient_name,recipient_phone,postal_code,address_line1,address_line2,created_at,updated_at)"
            + " VALUES (?,'test','recipient','01000000000','12345','test"
            + " address',NULL,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",
        member.getId());
    long addressId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update("UPDATE members SET default_address_id=? WHERE id=?", addressId, member.getId());
    jdbc.update(
        "INSERT INTO"
            + " billing_payment_methods(member_id,provider,customer_key,billing_key,status,created_at)"
            + " VALUES (?,'TOSS',?,?, 'ACTIVE',CURRENT_TIMESTAMP(6))",
        member.getId(),
        "customer-" + UUID.randomUUID(),
        "billing-" + UUID.randomUUID());
  }

  @AfterEach
  void tearDown() {
    reset(nativeJdbc);
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
        new SubscriptionCommandRequest(null, planVersionId, null, null, null, null));
    long originalSnapshotId =
        jdbc.queryForObject(
            "SELECT current_snapshot_id FROM subscriptions WHERE id=?", Long.class, subscriptionId);
    long pendingSnapshotId =
        jdbc.queryForObject(
            "SELECT snapshot_id FROM pending_plan_changes WHERE subscription_id=?",
            Long.class,
            subscriptionId);
    LocalDate yesterday = LocalDate.now(SEOUL).minusDays(1);
    jdbc.update(
        "UPDATE subscription_schedules SET scheduled_date=? WHERE subscription_id=?",
        yesterday,
        subscriptionId);

    service.reconcileActiveSubscriptions();

    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM subscriptions WHERE id=?", Long.class, subscriptionId))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "SELECT current_snapshot_id FROM subscriptions WHERE id=?",
                Long.class,
                subscriptionId))
        .isEqualTo(originalSnapshotId);
    assertThat(
            jdbc.queryForObject(
                "SELECT snapshot_id FROM pending_plan_changes WHERE subscription_id=?",
                Long.class,
                subscriptionId))
        .isEqualTo(pendingSnapshotId);
    assertThat(
            jdbc.queryForObject(
                "SELECT effective_snapshot_id FROM subscription_schedules WHERE subscription_id=?",
                Long.class,
                subscriptionId))
        .isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_orders WHERE subscription_id=?",
                Integer.class,
                subscriptionId))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND"
                    + " scheduled_date>?",
                Integer.class,
                subscriptionId,
                LocalDate.now(SEOUL)))
        .isZero();
  }

  @Test
  void failedProcessedResultRepairRollsBackAndNextSubscriptionCommits(CapturedOutput output) {
    long failedSubscriptionId = createProcessedSubscription("failed-repair");
    long successfulSubscriptionId = createProcessedSubscription("successful-repair");
    doAnswer(
            invocation -> {
              int updated = (int) invocation.callRealMethod();
              if (invocation.getArgument(1, Number.class).longValue() == failedSubscriptionId) {
                throw new IllegalStateException("intentional reconciliation repair failure");
              }
              return updated;
            })
        .when(nativeJdbc)
        .update(eq(INSERT_FUTURE_SCHEDULE), any(Object[].class));

    service.reconcileActiveSubscriptions();

    assertThat(futureScheduleCount(failedSubscriptionId)).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM subscriptions WHERE id=?", Long.class, failedSubscriptionId))
        .isEqualTo(1L);
    assertThat(futureScheduleCount(successfulSubscriptionId)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM subscriptions WHERE id=?",
                Long.class,
                successfulSubscriptionId))
        .isEqualTo(2L);
    assertThat(output)
        .contains(
            "Subscription reconciliation failed; subscriptionId=" + failedSubscriptionId,
            "intentional reconciliation repair failure");

    reset(nativeJdbc);
    service.reconcileActiveSubscriptions();

    assertThat(futureScheduleCount(failedSubscriptionId)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM subscriptions WHERE id=?", Long.class, failedSubscriptionId))
        .isEqualTo(2L);
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
    long petId =
        service
                    .createPet(member.getId(), new CreatePetRequest("보리", "DOG")).petId();
    SubscriptionResult result =
        service.createSubscription(
            member.getId(),
            idempotencyKey,
            new CreateSubscriptionRequest(petId, planVersionId, 4));
    return result.body().subscriptionId();
  }

  private void cleanFixtures() {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> cleanFixturesInTransaction());
  }

  private void cleanFixturesInTransaction() {
    String memberFilter =
        "SELECT id FROM members WHERE email LIKE '" + EMAIL_PREFIX + "%@example.test'";
    jdbc.update(
        "DELETE p FROM pending_plan_changes p JOIN subscriptions s ON s.id=p.subscription_id WHERE"
            + " s.member_id IN ("
            + memberFilter
            + ")");
    jdbc.update(
        "DELETE r FROM subscription_command_idempotency_results r JOIN subscriptions s ON"
            + " s.id=r.subscription_id WHERE s.member_id IN ("
            + memberFilter
            + ")");
    jdbc.update(
        "DELETE FROM subscription_creation_idempotency_results WHERE member_id IN ("
            + memberFilter
            + ")");
    jdbc.update(
        "DELETE h FROM subscription_command_history h JOIN subscriptions s ON"
            + " s.id=h.subscription_id WHERE s.member_id IN ("
            + memberFilter
            + ")");
    jdbc.update(
        "DELETE im FROM inventory_movements im JOIN payments p ON p.id=im.payment_id JOIN orders o"
            + " ON o.id=p.order_id JOIN subscription_order_context c ON c.order_id=o.id JOIN"
            + " subscriptions s ON s.id=c.subscription_id WHERE s.member_id IN ("
            + memberFilter
            + ")");
    jdbc.update(
        "DELETE p FROM payments p JOIN orders o ON o.id=p.order_id JOIN subscription_order_context"
            + " c ON c.order_id=o.id JOIN subscriptions s ON s.id=c.subscription_id WHERE"
            + " s.member_id IN ("
            + memberFilter
            + ")");
    jdbc.update(
        "DELETE oi FROM order_items oi JOIN subscription_order_context c ON c.order_id=oi.order_id"
            + " JOIN subscriptions s ON s.id=c.subscription_id WHERE s.member_id IN ("
            + memberFilter
            + ")");
    jdbc.update(
        "DELETE c FROM subscription_order_context c JOIN subscriptions s ON s.id=c.subscription_id"
            + " WHERE s.member_id IN ("
            + memberFilter
            + ")");
    jdbc.update(
        "DELETE FROM orders WHERE member_id IN (" + memberFilter + ") AND source='SUBSCRIPTION'");
    jdbc.update(
        "DELETE item FROM subscription_order_items item JOIN subscription_orders orders ON"
            + " orders.id=item.order_id JOIN subscriptions s ON s.id=orders.subscription_id WHERE"
            + " s.member_id IN ("
            + memberFilter
            + ")");
    jdbc.update(
        "DELETE orders FROM subscription_orders orders JOIN subscriptions s ON"
            + " s.id=orders.subscription_id WHERE s.member_id IN ("
            + memberFilter
            + ")");
    jdbc.update(
        "DELETE schedule FROM subscription_schedules schedule JOIN subscriptions s ON"
            + " s.id=schedule.subscription_id WHERE s.member_id IN ("
            + memberFilter
            + ")");
    jdbc.update(
        "DELETE item FROM subscription_snapshot_items item JOIN subscription_snapshots snapshot ON"
            + " snapshot.id=item.snapshot_id JOIN subscriptions s ON s.id=snapshot.subscription_id"
            + " WHERE s.member_id IN ("
            + memberFilter
            + ")");
    jdbc.update(
        "DELETE shipping FROM subscription_shipping_snapshots shipping JOIN subscriptions s ON"
            + " s.id=shipping.subscription_id WHERE s.member_id IN ("
            + memberFilter
            + ")");
    jdbc.update(
        "UPDATE subscriptions SET current_snapshot_id=NULL WHERE member_id IN ("
            + memberFilter
            + ")");
    jdbc.update(
        "DELETE snapshot FROM subscription_snapshots snapshot JOIN subscriptions s ON"
            + " s.id=snapshot.subscription_id WHERE s.member_id IN ("
            + memberFilter
            + ")");
    jdbc.update("DELETE FROM subscriptions WHERE member_id IN (" + memberFilter + ")");
    jdbc.update("DELETE FROM pets WHERE member_id IN (" + memberFilter + ")");
    jdbc.update(
        "UPDATE subscription_plans SET current_plan_version_id=NULL WHERE name LIKE ?",
        PLAN_PREFIX + "%");
    jdbc.update(
        "DELETE cycle FROM plan_version_delivery_cycles cycle JOIN plan_versions version ON"
            + " version.id=cycle.plan_version_id JOIN subscription_plans plan ON"
            + " plan.id=version.plan_id WHERE plan.name LIKE ?",
        PLAN_PREFIX + "%");
    jdbc.update(
        "DELETE item FROM plan_items item JOIN plan_versions version ON"
            + " version.id=item.plan_version_id JOIN subscription_plans plan ON"
            + " plan.id=version.plan_id WHERE plan.name LIKE ?",
        PLAN_PREFIX + "%");
    jdbc.update(
        "DELETE version FROM plan_versions version JOIN subscription_plans plan ON"
            + " plan.id=version.plan_id WHERE plan.name LIKE ?",
        PLAN_PREFIX + "%");
    jdbc.update("DELETE FROM subscription_plans WHERE name LIKE ?", PLAN_PREFIX + "%");
    jdbc.update(
        "DELETE inventory FROM inventories inventory JOIN skus sku ON sku.id=inventory.sku_id JOIN"
            + " products product ON product.id=sku.product_id WHERE product.name LIKE ?",
        PRODUCT_PREFIX + "%");
    jdbc.update(
        "DELETE sku FROM skus sku JOIN products product ON product.id=sku.product_id WHERE"
            + " product.name LIKE ?",
        PRODUCT_PREFIX + "%");
    jdbc.update("DELETE FROM products WHERE name LIKE ?", PRODUCT_PREFIX + "%");
    jdbc.update(
        "UPDATE members SET default_address_id=NULL WHERE email LIKE ?",
        EMAIL_PREFIX + "%@example.test");
    jdbc.update("DELETE FROM billing_payment_methods WHERE member_id IN (" + memberFilter + ")");
    jdbc.update("DELETE FROM member_addresses WHERE member_id IN (" + memberFilter + ")");
    jdbc.update("DELETE FROM members WHERE email LIKE ?", EMAIL_PREFIX + "%@example.test");
  }
}
