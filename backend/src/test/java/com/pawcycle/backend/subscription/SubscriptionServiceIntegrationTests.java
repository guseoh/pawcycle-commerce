package com.pawcycle.backend.subscription;

import com.pawcycle.backend.subscription.migration.LegacySubscriptionMigrationProcessor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawcycle.backend.subscription.api.CreatePetRequest;
import com.pawcycle.backend.subscription.api.CreateSubscriptionRequest;

import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.persistence.CategoryRepository;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.persistence.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.persistence.SkuRepository;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.persistence.MemberRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubscriptionServiceIntegrationTests {

  @Autowired private SubscriptionService service;
  @Autowired private LegacySubscriptionMigrationProcessor legacyMigration;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JdbcTemplate jdbcExecutor;
  @Autowired private MemberRepository members;
  @Autowired private ProductRepository products;
  @Autowired private SkuRepository skus;
  @Autowired private CategoryRepository categories;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private SubscriptionIdempotencyCleanupService cleanupService;
  @Autowired private MeterRegistry meterRegistry;
  @Autowired private PlatformTransactionManager transactionManager;
  private Member member;
  private Product product;
  private Sku sku;
  private long planId;
  private long planVersionId;

  @BeforeEach
  void setUp() {
    member =
        members.saveAndFlush(
            new Member(
                "v2-" + UUID.randomUUID() + "@example.test",
                passwordEncoder.encode("test-password")));
    product = new Product(activeCategory(), "Subscription plan product", "test", null, "DOG", null);
    product.transitionTo(com.pawcycle.backend.catalog.product.domain.ProductStatus.PUBLIC);
    product = products.saveAndFlush(product);
    sku =
        skus.saveAndFlush(
            com.pawcycle.backend.support.TestSkuFactory.sku(
                product, "v2-sku-" + UUID.randomUUID(), new BigDecimal("12000.00"), true, 1));
    jdbc.update(
        "INSERT INTO subscription_plans(name,target_pet_type,on_sale) VALUES (?,?,true)",
        "DOG starter",
        "DOG");
    planId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update(
        "INSERT INTO plan_versions(plan_id,package_price_krw,is_migration_only) VALUES"
            + " (?,24000,false)",
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
  }

  private Category activeCategory() {
    String suffix = UUID.randomUUID().toString();
    return categories.saveAndFlush(
        new Category("v2-service-" + suffix, "v2-service-" + suffix, 0, true));
  }

  @AfterEach
  void cleanCommittedFixtures() {
    if (TestTransaction.isActive() || member == null) {
      return;
    }

    long memberId = member.getId();
    Set<Long> planIds = new LinkedHashSet<>();
    if (planId != 0) {
      planIds.add(planId);
    }
    planIds.addAll(
        jdbc.queryForList(
            "SELECT DISTINCT pv.plan_id FROM subscription_snapshots ss "
                + "JOIN subscriptions s ON s.id=ss.subscription_id "
                + "JOIN plan_versions pv ON pv.id=ss.source_plan_version_id "
                + "WHERE s.member_id=?",
            Long.class,
            memberId));

    jdbc.update(
        "DELETE p FROM pending_plan_changes p JOIN subscriptions s ON s.id=p.subscription_id WHERE"
            + " s.member_id=?",
        memberId);
    jdbc.update(
        "DELETE r FROM subscription_command_idempotency_results r JOIN subscriptions s ON"
            + " s.id=r.subscription_id WHERE s.member_id=?",
        memberId);
    jdbc.update(
        "DELETE FROM subscription_creation_idempotency_results WHERE member_id=?", memberId);
    jdbc.update(
        "DELETE h FROM subscription_command_history h JOIN subscriptions s ON"
            + " s.id=h.subscription_id WHERE s.member_id=?",
        memberId);
    jdbc.update(
        "DELETE item FROM subscription_order_items item JOIN subscription_orders orders ON"
            + " orders.id=item.order_id JOIN subscriptions s ON s.id=orders.subscription_id WHERE"
            + " s.member_id=?",
        memberId);
    jdbc.update(
        "DELETE orders FROM subscription_orders orders JOIN subscriptions s ON"
            + " s.id=orders.subscription_id WHERE s.member_id=?",
        memberId);
    jdbc.update(
        "DELETE sc FROM subscription_schedules sc JOIN subscriptions s ON s.id=sc.subscription_id"
            + " WHERE s.member_id=?",
        memberId);
    jdbc.update(
        "DELETE si FROM subscription_snapshot_items si JOIN subscription_snapshots ss ON"
            + " ss.id=si.snapshot_id JOIN subscriptions s ON s.id=ss.subscription_id WHERE"
            + " s.member_id=?",
        memberId);
    jdbc.update("UPDATE subscriptions SET current_snapshot_id=NULL WHERE member_id=?", memberId);
    jdbc.update(
        "DELETE ss FROM subscription_snapshots ss JOIN subscriptions s ON s.id=ss.subscription_id"
            + " WHERE s.member_id=?",
        memberId);
    jdbc.update("DELETE FROM subscriptions WHERE member_id=?", memberId);
    jdbc.update("DELETE FROM pets WHERE member_id=?", memberId);

    for (Long fixturePlanId : planIds) {
      jdbc.update(
          "UPDATE subscription_plans SET current_plan_version_id=NULL WHERE id=?", fixturePlanId);
      jdbc.update(
          "DELETE c FROM plan_version_delivery_cycles c JOIN plan_versions v ON"
              + " v.id=c.plan_version_id WHERE v.plan_id=?",
          fixturePlanId);
      jdbc.update(
          "DELETE i FROM plan_items i JOIN plan_versions v ON v.id=i.plan_version_id WHERE"
              + " v.plan_id=?",
          fixturePlanId);
      jdbc.update("DELETE FROM plan_versions WHERE plan_id=?", fixturePlanId);
      jdbc.update("DELETE FROM subscription_plans WHERE id=?", fixturePlanId);
    }

    if (sku != null) {
      jdbc.update("DELETE FROM skus WHERE id=?", sku.getId());
    }
    if (product != null) {
      jdbc.update("DELETE FROM products WHERE id=?", product.getId());
    }
    jdbc.update("DELETE FROM members WHERE id=?", memberId);
  }

  @Test
  @SuppressWarnings("unchecked")
  void listPagesKeepRelatedRowsOrderingAndMemberIsolationAfterBatchAssembly() {
    long petId =
        service
                    .createPet(member.getId(), new CreatePetRequest("보리", "DOG")).petId();
    jdbc.update(
        "INSERT INTO subscription_plans(name,target_pet_type,on_sale) VALUES (?,?,true)",
        "DOG multi",
        "DOG");
    long multiPlanId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update(
        "INSERT INTO plan_versions(plan_id,package_price_krw,is_migration_only) VALUES"
            + " (?,24000,false)",
        multiPlanId);
    long multiVersionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update(
        "INSERT INTO plan_items(plan_version_id,sku_id,quantity) VALUES (?,?,?)",
        multiVersionId,
        sku.getId(),
        3);
    jdbc.update(
        "INSERT INTO plan_version_delivery_cycles(plan_version_id,delivery_cycle_weeks) VALUES"
            + " (?,?)",
        multiVersionId,
        2);
    jdbc.update(
        "INSERT INTO plan_version_delivery_cycles(plan_version_id,delivery_cycle_weeks) VALUES"
            + " (?,?)",
        multiVersionId,
        8);
    jdbc.update(
        "UPDATE subscription_plans SET current_plan_version_id=? WHERE id=?",
        multiVersionId,
        multiPlanId);
    jdbc.update(
        "INSERT INTO subscription_plans(name,target_pet_type,on_sale) VALUES (?,?,true)",
        "DOG empty",
        "DOG");
    long emptyPlanId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update(
        "INSERT INTO plan_versions(plan_id,package_price_krw,is_migration_only) VALUES"
            + " (?,24000,false)",
        emptyPlanId);
    long emptyVersionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update(
        "UPDATE subscription_plans SET current_plan_version_id=? WHERE id=?",
        emptyVersionId,
        emptyPlanId);

    var plans = service.plans(member.getId(), petId, 0, 100).items();
    assertThat(plans).extracting(plan -> plan.planId()).isSorted();
    assertThat(plans)
        .filteredOn(plan -> "DOG multi".equals(plan.planName()))
        .singleElement()
        .satisfies(
            plan -> {
              assertThat(plan.items())
                  .singleElement()
                  .satisfies(
                      item -> {
                        assertThat(item.skuId()).isEqualTo(sku.getId());
                        assertThat(item.quantity()).isEqualTo(3);
                      });
              assertThat(plan.allowedDeliveryCycleWeeks()).containsExactly(2, 8);
            });
    assertThat(plans)
        .filteredOn(plan -> "DOG empty".equals(plan.planName()))
        .singleElement()
        .satisfies(
            plan -> {
              assertThat(plan.items()).isEmpty();
              assertThat(plan.allowedDeliveryCycleWeeks()).isEmpty();
            });
    var secondPlanPage = service.plans(member.getId(), petId, 1, 1).items();
    assertThat(secondPlanPage).containsExactly(plans.get(1));

    long firstSubscriptionId =
        service
                    .createSubscription(
                        member.getId(),
                        "list-first",
                        new CreateSubscriptionRequest(petId, planVersionId, 4))
                    .body().subscriptionId();
    long secondSubscriptionId =
        service
                    .createSubscription(
                        member.getId(),
                        "list-second",
                        new CreateSubscriptionRequest(petId, multiVersionId, 2))
                    .body().subscriptionId();
    Sku secondSku =
        skus.saveAndFlush(
            com.pawcycle.backend.support.TestSkuFactory.sku(
                sku.getProduct(),
                "v2-list-sku-" + UUID.randomUUID(),
                new BigDecimal("13000.00"),
                true,
                1));
    long secondSnapshotId =
        jdbc.queryForObject(
            "SELECT current_snapshot_id FROM subscriptions WHERE id=?",
            Long.class,
            secondSubscriptionId);
    jdbc.update(
        "INSERT INTO subscription_snapshot_items(snapshot_id,sku_id,quantity) VALUES (?,?,?)",
        secondSnapshotId,
        secondSku.getId(),
        1);
    long firstSnapshotId =
        jdbc.queryForObject(
            "SELECT current_snapshot_id FROM subscriptions WHERE id=?",
            Long.class,
            firstSubscriptionId);
    jdbc.update("DELETE FROM subscription_snapshot_items WHERE snapshot_id=?", firstSnapshotId);
    jdbc.update(
        "INSERT INTO subscription_schedules(subscription_id,scheduled_date,status) VALUES"
            + " (?,?,'SCHEDULED')",
        secondSubscriptionId,
        LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1));
    Member other =
        members.saveAndFlush(
            new Member(
                "v2-other-" + UUID.randomUUID() + "@example.test",
                passwordEncoder.encode("test-password")));
    long otherPetId =
        service
                    .createPet(other.getId(), new CreatePetRequest("다른 회원", "DOG")).petId();
    service.createSubscription(
        other.getId(),
        "list-other",
        new CreateSubscriptionRequest(otherPetId, planVersionId, 4));

    var subscriptions = service.subscriptions(member.getId(), 0, 100).items();
    assertThat(subscriptions)
        .extracting(subscription -> subscription.subscriptionId())
        .containsExactly(secondSubscriptionId, firstSubscriptionId);
    var second = subscriptions.getFirst();
    var secondPet = second.pet();
    assertThat(secondPet.petId()).isEqualTo(petId);
    assertThat(secondPet.name()).isEqualTo("보리");
    assertThat(secondPet.petType()).isEqualTo("DOG");
    assertThat(secondPet.breed()).isNull();
    assertThat(secondPet.weightKg()).isNull();
    assertThat(secondPet.profileComplete()).isFalse();
    assertThat(second.currentSnapshot().items())
        .satisfiesExactly(
            item -> {
              assertThat(item.skuId()).isEqualTo(sku.getId());
              assertThat(item.quantity()).isEqualTo(3);
            },
            item -> {
              assertThat(item.skuId()).isEqualTo(secondSku.getId());
              assertThat(item.quantity()).isEqualTo(1);
            });
    assertThat(second.nextScheduledDate())
        .isEqualTo(LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1));
    assertThat(subscriptions.get(1).currentSnapshot().items()).isEmpty();
    var secondSubscriptionPage = service.subscriptions(member.getId(), 1, 1).items();
    assertThat(secondSubscriptionPage).containsExactly(subscriptions.get(1));
  }

  @Test
  void mysqlPersistsReplayAndRejectsMismatchedPayload() {
    long petId =
        service
                    .createPet(member.getId(), new CreatePetRequest("보리", "DOG")).petId();
    CreateSubscriptionRequest request = new CreateSubscriptionRequest(petId, planVersionId, 4);
    SubscriptionResult created =
        service.createSubscription(member.getId(), "create-replay-key", request);
    LocalDateTime completedAt =
        jdbc.queryForObject(
            "SELECT completed_at FROM subscription_creation_idempotency_results WHERE member_id=?"
                + " AND idempotency_key=?",
            LocalDateTime.class,
            member.getId(),
            "create-replay-key");
    assertThat(
            jdbc.update(
                "UPDATE subscription_creation_idempotency_results SET"
                    + " response_body=JSON_SET(response_body,'$.currentSnapshot.snapshotId',9001)"
                    + " WHERE member_id=? AND idempotency_key=?",
                member.getId(),
                "create-replay-key"))
        .isEqualTo(1);
    SubscriptionResult replay =
        service.createSubscription(
            member.getId(),
            "create-replay-key",
            new CreateSubscriptionRequest(petId, planVersionId, 4));

    assertThat(created.status()).isEqualTo(201);
    assertThat(completedAt).isNotNull();
    assertThat(created.body().pet()).isNotNull();
    assertThat(created.body().currentSnapshot()).isNotNull();
    assertThat(created.body().schedules()).isNotNull();
    assertThat(created.body().commandHistory()).isNotNull();
    assertThat(replay.replay()).isTrue();
    assertThat(replay.body().currentSnapshot()).isNotNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT response_body FROM subscription_creation_idempotency_results WHERE"
                    + " member_id=? AND idempotency_key=?",
                String.class,
                member.getId(),
                "create-replay-key"))
        .doesNotContain("\"snapshotId\"");
    assertThat(
            jdbc.queryForObject(
                "SELECT completed_at FROM subscription_creation_idempotency_results WHERE"
                    + " member_id=? AND idempotency_key=?",
                LocalDateTime.class,
                member.getId(),
                "create-replay-key"))
        .isEqualTo(completedAt);
    assertThatThrownBy(
            () ->
                service.createSubscription(
                    member.getId(),
                    "create-replay-key",
                    new CreateSubscriptionRequest(petId, planVersionId, 2)))
        .isInstanceOf(SubscriptionApiException.class)
        .hasFieldOrPropertyWithValue("code", "IDEMPOTENCY_KEY_REUSED");
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void cleanupDeletesOnlyExpiredRowsWithinEachTableBatch() {
    long petId =
        service
                    .createPet(member.getId(), new CreatePetRequest("보리", "DOG")).petId();
    long subscriptionId =
        service
                    .createSubscription(
                        member.getId(),
                        "cleanup-subscription",
                        new CreateSubscriptionRequest(petId, planVersionId, 4))
                    .body().subscriptionId();
    jdbc.update(
        "DELETE FROM subscription_creation_idempotency_results WHERE member_id=? AND"
            + " idempotency_key=?",
        member.getId(),
        "cleanup-subscription");

    Instant now = Instant.parse("2026-08-08T00:00:00Z");
    LocalDateTime cutoff =
        LocalDateTime.ofInstant(now.minusSeconds(30L * 24 * 60 * 60), ZoneOffset.UTC);
    insertCreationResult("creation-expired-old", cutoff.minusSeconds(3));
    insertCreationResult("creation-expired-middle", cutoff.minusSeconds(2));
    insertCreationResult("creation-expired-near", cutoff.minusSeconds(1));
    insertCreationResult("creation-cutoff", cutoff);
    insertCreationResult("creation-recent", cutoff.plusSeconds(1));
    insertCreationReservation("creation-incomplete");
    insertCommandResult(subscriptionId, "command-expired-old", cutoff.minusSeconds(3));
    insertCommandResult(subscriptionId, "command-expired-middle", cutoff.minusSeconds(2));
    insertCommandResult(subscriptionId, "command-expired-near", cutoff.minusSeconds(1));
    insertCommandResult(subscriptionId, "command-cutoff", cutoff);
    insertCommandResult(subscriptionId, "command-recent", cutoff.plusSeconds(1));
    insertCommandReservation(subscriptionId, "command-incomplete");

    Clock cleanupClock = Clock.fixed(now, ZoneOffset.UTC);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    SubscriptionMetrics metrics =
        new SubscriptionMetrics(
            registry,
            new com.pawcycle.backend.subscription.persistence.SubscriptionMetricsQueryRepository(
                jdbcExecutor),
            cleanupClock);
    SubscriptionIdempotencyCleanupProcessor cleanup =
        new SubscriptionIdempotencyCleanupProcessor(jdbcExecutor, cleanupClock, metrics);
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    SubscriptionIdempotencyCleanupResult first =
        transaction.execute(status -> cleanup.deleteExpired(2));

    assertThat(first.creationRepaired()).isZero();
    assertThat(first.commandRepaired()).isZero();
    assertThat(first.creationDeleted()).isEqualTo(2);
    assertThat(first.commandDeleted()).isEqualTo(2);
    assertThat(creationResultExists("creation-expired-near")).isTrue();
    assertThat(commandResultExists(subscriptionId, "command-expired-near")).isTrue();
    assertThat(creationResultExists("creation-cutoff")).isTrue();
    assertThat(commandResultExists(subscriptionId, "command-cutoff")).isTrue();
    assertThat(creationResultExists("creation-recent")).isTrue();
    assertThat(commandResultExists(subscriptionId, "command-recent")).isTrue();
    assertThat(creationResultExists("creation-incomplete")).isTrue();
    assertThat(commandResultExists(subscriptionId, "command-incomplete")).isTrue();

    SubscriptionIdempotencyCleanupResult second =
        transaction.execute(status -> cleanup.deleteExpired(2));
    metrics.refreshIdempotencyGauges();

    assertThat(second.creationRepaired()).isZero();
    assertThat(second.commandRepaired()).isZero();
    assertThat(second.creationDeleted()).isEqualTo(1);
    assertThat(second.commandDeleted()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_creation_idempotency_results WHERE member_id=?"
                    + " AND completed_at<?",
                Integer.class,
                member.getId(),
                cutoff))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_command_idempotency_results WHERE member_id=?"
                    + " AND completed_at<?",
                Integer.class,
                member.getId(),
                cutoff))
        .isZero();
    assertThat(
            registry
                .get("pawcycle.subscription.idempotency.cleanup.rows")
                .tags("scope", "creation", "operation", "delete")
                .counter()
                .count())
        .isEqualTo(3);
    assertThat(
            registry
                .get("pawcycle.subscription.idempotency.cleanup.rows")
                .tags("scope", "command", "operation", "delete")
                .counter()
                .count())
        .isEqualTo(3);
    assertThatThrownBy(() -> cleanup.deleteExpired(0)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void cleanupRepairsRollbackEraSuccessRowsWithinEachTableBatchBeforeDeleting() {
    long petId =
        service
                    .createPet(member.getId(), new CreatePetRequest("보리", "DOG")).petId();
    long subscriptionId =
        service
                    .createSubscription(
                        member.getId(),
                        "repair-subscription",
                        new CreateSubscriptionRequest(petId, planVersionId, 4))
                    .body().subscriptionId();
    jdbc.update(
        "DELETE FROM subscription_creation_idempotency_results WHERE member_id=? AND"
            + " idempotency_key=?",
        member.getId(),
        "repair-subscription");
    insertCreationResult("creation-repair-a", null);
    insertCreationResult("creation-repair-b", null);
    insertCreationResult("creation-repair-c", null);
    insertCreationReservation("creation-repair-incomplete");
    insertCommandResult(subscriptionId, "command-repair-a", null);
    insertCommandResult(subscriptionId, "command-repair-b", null);
    insertCommandResult(subscriptionId, "command-repair-c", null);
    insertCommandReservation(subscriptionId, "command-repair-incomplete");

    Instant now = Instant.parse("2026-08-09T00:00:00Z");
    LocalDateTime expectedCompletedAt = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
    Clock cleanupClock = Clock.fixed(now, ZoneOffset.UTC);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    SubscriptionMetrics metrics =
        new SubscriptionMetrics(
            registry,
            new com.pawcycle.backend.subscription.persistence.SubscriptionMetricsQueryRepository(
                jdbcExecutor),
            cleanupClock);
    SubscriptionIdempotencyCleanupProcessor cleanup =
        new SubscriptionIdempotencyCleanupProcessor(jdbcExecutor, cleanupClock, metrics);

    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    SubscriptionIdempotencyCleanupResult first =
        transaction.execute(status -> cleanup.deleteExpired(2));

    assertThat(first.creationRepaired()).isEqualTo(2);
    assertThat(first.commandRepaired()).isEqualTo(2);
    assertThat(first.creationDeleted()).isZero();
    assertThat(first.commandDeleted()).isZero();
    assertThat(resultCompletedAt("subscription_creation_idempotency_results", "creation-repair-a"))
        .isEqualTo(expectedCompletedAt);
    assertThat(resultCompletedAt("subscription_creation_idempotency_results", "creation-repair-b"))
        .isEqualTo(expectedCompletedAt);
    assertThat(resultCompletedAt("subscription_creation_idempotency_results", "creation-repair-c"))
        .isNull();
    assertThat(
            resultCompletedAt(
                "subscription_creation_idempotency_results", "creation-repair-incomplete"))
        .isNull();
    assertThat(resultCompletedAt("subscription_command_idempotency_results", "command-repair-a"))
        .isEqualTo(expectedCompletedAt);
    assertThat(resultCompletedAt("subscription_command_idempotency_results", "command-repair-b"))
        .isEqualTo(expectedCompletedAt);
    assertThat(resultCompletedAt("subscription_command_idempotency_results", "command-repair-c"))
        .isNull();
    assertThat(
            resultCompletedAt(
                "subscription_command_idempotency_results", "command-repair-incomplete"))
        .isNull();

    SubscriptionIdempotencyCleanupResult second =
        transaction.execute(status -> cleanup.deleteExpired(2));
    metrics.refreshIdempotencyGauges();

    assertThat(second.creationRepaired()).isEqualTo(1);
    assertThat(second.commandRepaired()).isEqualTo(1);
    assertThat(second.creationDeleted()).isZero();
    assertThat(second.commandDeleted()).isZero();
    assertThat(resultCompletedAt("subscription_creation_idempotency_results", "creation-repair-c"))
        .isEqualTo(expectedCompletedAt);
    assertThat(resultCompletedAt("subscription_command_idempotency_results", "command-repair-c"))
        .isEqualTo(expectedCompletedAt);
    assertThat(
            resultCompletedAt(
                "subscription_creation_idempotency_results", "creation-repair-incomplete"))
        .isNull();
    assertThat(
            resultCompletedAt(
                "subscription_command_idempotency_results", "command-repair-incomplete"))
        .isNull();
    assertThat(
            registry
                .get("pawcycle.subscription.idempotency.cleanup.executions")
                .tag("result", "success")
                .counter()
                .count())
        .isEqualTo(2);
    assertThat(
            registry
                .get("pawcycle.subscription.idempotency.cleanup.executions")
                .tag("result", "failure")
                .counter()
                .count())
        .isZero();
    assertThat(registry.get("pawcycle.subscription.idempotency.cleanup.duration").timer().count())
        .isEqualTo(2);
    assertThat(
            registry
                .get("pawcycle.subscription.idempotency.cleanup.rows")
                .tags("scope", "creation", "operation", "repair")
                .counter()
                .count())
        .isEqualTo(3);
    assertThat(
            registry
                .get("pawcycle.subscription.idempotency.cleanup.rows")
                .tags("scope", "command", "operation", "repair")
                .counter()
                .count())
        .isEqualTo(3);
    assertThat(
            registry
                .get("pawcycle.subscription.idempotency.retained.rows")
                .tag("scope", "creation")
                .gauge()
                .value())
        .isEqualTo(
            jdbc.queryForObject(
                    "SELECT COUNT(*) FROM subscription_creation_idempotency_results WHERE"
                        + " completed_at IS NOT NULL",
                    Long.class)
                .doubleValue());
    assertThat(
            registry
                .get("pawcycle.subscription.idempotency.retained.rows")
                .tag("scope", "command")
                .gauge()
                .value())
        .isEqualTo(
            jdbc.queryForObject(
                    "SELECT COUNT(*) FROM subscription_command_idempotency_results WHERE"
                        + " completed_at IS NOT NULL",
                    Long.class)
                .doubleValue());
    assertThat(
            registry
                .get("pawcycle.subscription.idempotency.cleanup.candidates")
                .tag("scope", "creation")
                .gauge()
                .value())
        .isZero();
    assertThat(
            registry
                .get("pawcycle.subscription.idempotency.cleanup.candidates")
                .tag("scope", "command")
                .gauge()
                .value())
        .isZero();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void cleanupCommitFailureRecordsFailureMetricsAfterRollback() {
    String idempotencyKey = "cleanup-commit-failure";
    insertCreationResult(idempotencyKey, LocalDateTime.of(2000, 1, 1, 0, 0));
    double successesBefore =
        meterRegistry
            .get("pawcycle.subscription.idempotency.cleanup.executions")
            .tag("result", "success")
            .counter()
            .count();
    double failuresBefore =
        meterRegistry
            .get("pawcycle.subscription.idempotency.cleanup.executions")
            .tag("result", "failure")
            .counter()
            .count();
    long durationCountBefore =
        meterRegistry.get("pawcycle.subscription.idempotency.cleanup.duration").timer().count();
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);

    assertThatThrownBy(
            () ->
                transaction.executeWithoutResult(
                    status -> {
                      TransactionSynchronizationManager.registerSynchronization(
                          new TransactionSynchronization() {
                            @Override
                            public void beforeCommit(boolean readOnly) {
                              throw new IllegalStateException(
                                  "OBS-BASE-001 intentional commit failure");
                            }
                          });
                      cleanupService.deleteExpired(1);
                    }))
        .isInstanceOf(RuntimeException.class);

    assertThat(creationResultExists(idempotencyKey)).isTrue();
    assertThat(
            meterRegistry
                .get("pawcycle.subscription.idempotency.cleanup.executions")
                .tag("result", "success")
                .counter()
                .count())
        .isEqualTo(successesBefore);
    assertThat(
            meterRegistry
                .get("pawcycle.subscription.idempotency.cleanup.executions")
                .tag("result", "failure")
                .counter()
                .count())
        .isEqualTo(failuresBefore + 1);
    assertThat(
            meterRegistry.get("pawcycle.subscription.idempotency.cleanup.duration").timer().count())
        .isEqualTo(durationCountBefore + 1);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void legacyWhitespacePetTypeNormalizesHidesV1AndDoesNotConsumeDueSchedule() {
    jdbc.update("UPDATE products SET pet_type=' DOG ' WHERE id=?", sku.getProduct().getId());
    jdbc.update(
        "INSERT INTO"
            + " subscriptions(member_id,sku_id,quantity,delivery_cycle_weeks,created_date,next_order_date)"
            + " VALUES (?,?,?,?,?,?)",
        member.getId(),
        sku.getId(),
        1,
        4,
        LocalDate.now(ZoneId.of("Asia/Seoul")).minusWeeks(4),
        LocalDate.now(ZoneId.of("Asia/Seoul")).plusWeeks(4));
    long subscriptionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

    assertThat(legacyMigration.preflight().valid()).isTrue();
    legacyMigration.migrateAfterSourceWriteFreeze(true);
    assertThat(
            jdbc.queryForObject(
                "SELECT legacy_api_visible FROM subscriptions WHERE id=?",
                Boolean.class,
                subscriptionId))
        .isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT target_pet_type FROM subscription_plans WHERE name IS NULL ORDER BY id DESC"
                    + " LIMIT 1",
                String.class))
        .isEqualTo("DOG");
    assertThat(
            jdbc.queryForObject(
                "SELECT effective_snapshot_id FROM subscription_schedules WHERE subscription_id=?",
                Long.class,
                subscriptionId))
        .isNull();

    jdbc.update(
        "UPDATE subscription_schedules SET scheduled_date=? WHERE subscription_id=?",
        LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1),
        subscriptionId);

    service.reconcileActiveSubscriptions();

    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND"
                    + " status='SCHEDULED' AND scheduled_date<=? AND effective_snapshot_id IS NULL",
                Integer.class,
                subscriptionId,
                LocalDate.now(ZoneId.of("Asia/Seoul"))))
        .isEqualTo(1);
  }

  private void insertCreationResult(String key, LocalDateTime completedAt) {
    jdbc.update(
        "INSERT INTO"
            + " subscription_creation_idempotency_results(member_id,idempotency_key,payload_fingerprint,response_status,response_body,completed_at)"
            + " VALUES (?,?,?,200,JSON_OBJECT(),?)",
        member.getId(),
        key,
        "0".repeat(64),
        completedAt);
  }

  private void insertCommandResult(long subscriptionId, String key, LocalDateTime completedAt) {
    jdbc.update(
        "INSERT INTO"
            + " subscription_command_idempotency_results(member_id,subscription_id,command_type,idempotency_key,payload_fingerprint,response_status,response_body,completed_at)"
            + " VALUES (?,?,'PAUSE',?,?,200,JSON_OBJECT(),?)",
        member.getId(),
        subscriptionId,
        key,
        "0".repeat(64),
        completedAt);
  }

  private void insertCreationReservation(String key) {
    jdbc.update(
        "INSERT INTO"
            + " subscription_creation_idempotency_results(member_id,idempotency_key,payload_fingerprint)"
            + " VALUES (?,?,?)",
        member.getId(),
        key,
        "1".repeat(64));
  }

  private void insertCommandReservation(long subscriptionId, String key) {
    jdbc.update(
        "INSERT INTO"
            + " subscription_command_idempotency_results(member_id,subscription_id,command_type,idempotency_key,payload_fingerprint)"
            + " VALUES (?,?,'PAUSE',?,?)",
        member.getId(),
        subscriptionId,
        key,
        "1".repeat(64));
  }

  private LocalDateTime resultCompletedAt(String table, String key) {
    return jdbc.queryForObject(
        "SELECT completed_at FROM " + table + " WHERE member_id=? AND idempotency_key=?",
        LocalDateTime.class,
        member.getId(),
        key);
  }

  private boolean creationResultExists(String key) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM subscription_creation_idempotency_results WHERE member_id=? AND"
                + " idempotency_key=?",
            Integer.class,
            member.getId(),
            key)
        == 1;
  }

  private boolean commandResultExists(long subscriptionId, String key) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM subscription_command_idempotency_results WHERE member_id=? AND"
                + " subscription_id=? AND command_type='PAUSE' AND idempotency_key=?",
            Integer.class,
            member.getId(),
            subscriptionId,
            key)
        == 1;
  }
}
