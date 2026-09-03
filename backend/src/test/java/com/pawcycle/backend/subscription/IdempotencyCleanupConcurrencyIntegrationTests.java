package com.pawcycle.backend.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.persistence.CategoryRepository;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.persistence.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.persistence.SkuRepository;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.persistence.MemberRepository;
import com.pawcycle.backend.subscription.api.CreatePetRequest;
import com.pawcycle.backend.subscription.api.CreateSubscriptionRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class IdempotencyCleanupConcurrencyIntegrationTests {

  private static final long AWAIT_SECONDS = 15;

  @Autowired private SubscriptionService service;
  @Autowired private SubscriptionIdempotencyCleanupService cleanup;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MemberRepository members;
  @Autowired private ProductRepository products;
  @Autowired private SkuRepository skus;
  @Autowired private CategoryRepository categories;
  @Autowired private PasswordEncoder passwordEncoder;

  @AfterEach
  void cleanFixtures() {
    String memberFilter =
        "SELECT id FROM members WHERE email LIKE 'v2-cleanup-race-%@example.test'";
    jdbc.update(
        "DELETE FROM pending_plan_changes WHERE subscription_id IN (SELECT id FROM subscriptions"
            + " WHERE member_id IN ("
            + memberFilter
            + "))");
    jdbc.update(
        "DELETE result FROM subscription_command_idempotency_results result JOIN subscriptions"
            + " subscription ON subscription.id=result.subscription_id WHERE subscription.member_id"
            + " IN ("
            + memberFilter
            + ")");
    jdbc.update(
        "DELETE history FROM subscription_command_history history JOIN subscriptions subscription"
            + " ON subscription.id=history.subscription_id WHERE subscription.member_id IN ("
            + memberFilter
            + ")");
    jdbc.update(
        "DELETE addon FROM subscription_schedule_addons addon JOIN subscription_schedules schedule"
            + " ON schedule.id=addon.schedule_id WHERE schedule.subscription_id IN (SELECT id FROM"
            + " subscriptions WHERE member_id IN ("
            + memberFilter
            + "))");
    jdbc.update(
        "DELETE shipping FROM subscription_shipping_snapshots shipping JOIN subscriptions"
            + " subscription ON subscription.id=shipping.subscription_id WHERE subscription.member_id"
            + " IN ("
            + memberFilter
            + ")");
    jdbc.update(
        "DELETE FROM subscription_schedules WHERE subscription_id IN (SELECT id FROM subscriptions"
            + " WHERE member_id IN ("
            + memberFilter
            + "))");
    jdbc.update(
        "DELETE item FROM subscription_snapshot_items item JOIN subscription_snapshots snapshot ON"
            + " snapshot.id=item.snapshot_id WHERE snapshot.subscription_id IN (SELECT id FROM"
            + " subscriptions WHERE member_id IN ("
            + memberFilter
            + "))");
    jdbc.update(
        "UPDATE subscriptions SET current_snapshot_id=NULL WHERE member_id IN ("
            + memberFilter
            + ")");
    jdbc.update(
        "DELETE snapshot FROM subscription_snapshots snapshot JOIN subscriptions subscription ON"
            + " subscription.id=snapshot.subscription_id WHERE subscription.member_id IN ("
            + memberFilter
            + ")");
    jdbc.update("DELETE FROM subscription_creation_idempotency_results WHERE member_id IN (" + memberFilter + ")");
    jdbc.update("DELETE FROM subscriptions WHERE member_id IN (" + memberFilter + ")");
    jdbc.update("DELETE FROM pets WHERE member_id IN (" + memberFilter + ")");
    jdbc.update(
        "UPDATE subscription_plans SET current_plan_version_id=NULL WHERE name='DOG cleanup race plan'");
    jdbc.update(
        "DELETE cycle FROM plan_version_delivery_cycles cycle JOIN plan_versions version ON"
            + " version.id=cycle.plan_version_id JOIN subscription_plans plan ON plan.id=version.plan_id"
            + " WHERE plan.name='DOG cleanup race plan'");
    jdbc.update(
        "DELETE item FROM plan_items item JOIN plan_versions version ON version.id=item.plan_version_id"
            + " JOIN subscription_plans plan ON plan.id=version.plan_id WHERE plan.name='DOG cleanup race plan'");
    jdbc.update(
        "DELETE version FROM plan_versions version JOIN subscription_plans plan ON"
            + " plan.id=version.plan_id WHERE plan.name='DOG cleanup race plan'");
    jdbc.update("DELETE FROM subscription_plans WHERE name='DOG cleanup race plan'");
    jdbc.update(
        "DELETE inventory FROM inventories inventory JOIN skus sku ON sku.id=inventory.sku_id JOIN"
            + " products product ON product.id=sku.product_id WHERE product.name='Subscription cleanup race product'");
    jdbc.update(
        "DELETE sku FROM skus sku JOIN products product ON product.id=sku.product_id WHERE"
            + " product.name='Subscription cleanup race product'");
    jdbc.update("DELETE FROM products WHERE name='Subscription cleanup race product'");
    jdbc.update("DELETE FROM categories WHERE slug LIKE 'v2-cleanup-%'");
    jdbc.update("DELETE FROM members WHERE email LIKE 'v2-cleanup-race-%@example.test'");
  }
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void replayHoldingRowLockReturnsStoredResultBeforeCleanupDeletesExpiredRow() throws Exception {
    Fixture fixture = createFixture("replay-first");
    LocalDateTime initialCompletedAt = expire(fixture);
    TransactionTemplate replayTransaction = new TransactionTemplate(transactionManager);
    TransactionTemplate cleanupTransaction = new TransactionTemplate(transactionManager);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch replayLocked = new CountDownLatch(1);
    CountDownLatch cleanupStarted = new CountDownLatch(1);
    CountDownLatch cleanupFinished = new CountDownLatch(1);
    CountDownLatch allowReplayCommit = new CountDownLatch(1);
    try {
      Future<ReplayObservation> replay =
          executor.submit(
              () ->
                  replayTransaction.execute(
                      status -> {
                        SubscriptionResult result =
                            service.createSubscription(
                                fixture.memberId(), fixture.key(), fixture.request());
                        LocalDateTime completedAt = completedAt(fixture.memberId(), fixture.key());
                        replayLocked.countDown();
                        await(allowReplayCommit);
                        return new ReplayObservation(result, completedAt);
                      }));
      assertThat(replayLocked.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

      Future<SubscriptionIdempotencyCleanupResult> cleanupResult =
          executor.submit(
              () ->
                  cleanupTransaction.execute(
                      status -> {
                        cleanupStarted.countDown();
                        try {
                          return cleanup.deleteExpired(1);
                        } finally {
                          cleanupFinished.countDown();
                        }
                      }));
      assertThat(cleanupStarted.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
      assertThat(cleanupFinished.await(250, TimeUnit.MILLISECONDS)).isFalse();

      allowReplayCommit.countDown();
      ReplayObservation observation = replay.get(AWAIT_SECONDS, TimeUnit.SECONDS);
      SubscriptionIdempotencyCleanupResult deleted =
          cleanupResult.get(AWAIT_SECONDS, TimeUnit.SECONDS);

      assertThat(observation.result().replay()).isTrue();
      assertThat(observation.result().body().subscriptionId())
          .isEqualTo(fixture.subscriptionId());
      assertThat(observation.completedAt()).isEqualTo(initialCompletedAt);
      assertThat(deleted.creationDeleted()).isEqualTo(1);
      assertThat(deleted.commandDeleted()).isZero();
      assertThat(resultExists(fixture.memberId(), fixture.key())).isFalse();
    } finally {
      allowReplayCommit.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void cleanupCommittedFirstMakesSameKeyANewRequestWithNewCompletion() throws Exception {
    Fixture fixture = createFixture("cleanup-first");
    LocalDateTime initialCompletedAt = expire(fixture);
    TransactionTemplate cleanupTransaction = new TransactionTemplate(transactionManager);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<SubscriptionIdempotencyCleanupResult> cleanupResult =
          executor.submit(() -> cleanupTransaction.execute(status -> cleanup.deleteExpired(1)));
      SubscriptionIdempotencyCleanupResult deleted =
          cleanupResult.get(AWAIT_SECONDS, TimeUnit.SECONDS);
      assertThat(deleted.creationDeleted()).isEqualTo(1);
      assertThat(resultExists(fixture.memberId(), fixture.key())).isFalse();

      SubscriptionResult created =
          service.createSubscription(fixture.memberId(), fixture.key(), fixture.request());
      LocalDateTime newCompletedAt = completedAt(fixture.memberId(), fixture.key());

      assertThat(created.replay()).isFalse();
      assertThat(created.body().subscriptionId())
          .isNotEqualTo(fixture.subscriptionId());
      assertThat(resultExists(fixture.memberId(), fixture.key())).isTrue();
      assertThat(newCompletedAt).isAfter(initialCompletedAt);
    } finally {
      executor.shutdownNow();
    }
  }

  private Fixture createFixture(String prefix) {
    Member member =
        members.saveAndFlush(
            new Member(
                "v2-cleanup-race-" + UUID.randomUUID() + "@example.test",
                passwordEncoder.encode("test-password")));
    String categorySuffix = UUID.randomUUID().toString();
    Product product =
        new Product(
            categories.saveAndFlush(
                new Category(
                    "v2-cleanup-" + categorySuffix, "v2-cleanup-" + categorySuffix, 0, true)),
            "Subscription cleanup race product",
            "test",
            null,
            "DOG",
            null);
    product.transitionTo(com.pawcycle.backend.catalog.product.domain.ProductStatus.PUBLIC);
    product = products.saveAndFlush(product);
    Sku sku =
        skus.saveAndFlush(
            com.pawcycle.backend.support.TestSkuFactory.sku(
                product,
                "v2-cleanup-race-sku-" + UUID.randomUUID(),
                new BigDecimal("12000.00"),
                true,
                1));
    jdbc.update(
        "INSERT INTO subscription_plans(name,target_pet_type,on_sale) VALUES (?,?,true)",
        "DOG cleanup race plan",
        "DOG");
    long planId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update(
        "INSERT INTO plan_versions(plan_id,package_price_krw,is_migration_only) VALUES"
            + " (?,24000,false)",
        planId);
    long planVersionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
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
    long petId =
        service
                    .createPet(member.getId(), new CreatePetRequest("동시성 반려동물", "DOG")).petId();
    CreateSubscriptionRequest request = new CreateSubscriptionRequest(petId, planVersionId, 4);
    String key = prefix + "-" + UUID.randomUUID();
    SubscriptionResult created =
        service.createSubscription(member.getId(), key, request);
    return new Fixture(
        member.getId(), key, request, created.body().subscriptionId());
  }

  private LocalDateTime expire(Fixture fixture) {
    assertThat(
            jdbc.update(
                "UPDATE subscription_creation_idempotency_results SET"
                    + " completed_at=UTC_TIMESTAMP(6)-INTERVAL 31 DAY WHERE member_id=? AND"
                    + " idempotency_key=?",
                fixture.memberId(),
                fixture.key()))
        .isEqualTo(1);
    return completedAt(fixture.memberId(), fixture.key());
  }

  private LocalDateTime completedAt(long memberId, String key) {
    return jdbc.queryForObject(
        "SELECT completed_at FROM subscription_creation_idempotency_results WHERE member_id=? AND"
            + " idempotency_key=?",
        LocalDateTime.class,
        memberId,
        key);
  }

  private boolean resultExists(long memberId, String key) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM subscription_creation_idempotency_results WHERE member_id=? AND"
                + " idempotency_key=?",
            Integer.class,
            memberId,
            key)
        == 1;
  }

  private void await(CountDownLatch latch) {
    try {
      if (!latch.await(AWAIT_SECONDS, TimeUnit.SECONDS)) {
        throw new AssertionError("Timed out waiting for transaction coordination");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Interrupted while coordinating transactions", exception);
    }
  }

  private record Fixture(
      long memberId, String key, CreateSubscriptionRequest request, long subscriptionId) {}

  private record ReplayObservation(
      SubscriptionResult result, LocalDateTime completedAt) {}
}
