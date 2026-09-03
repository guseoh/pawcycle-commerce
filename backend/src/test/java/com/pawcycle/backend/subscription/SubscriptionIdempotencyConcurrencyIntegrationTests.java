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
import com.pawcycle.backend.subscription.api.SubscriptionCommandRequest;
import java.math.BigDecimal;
import java.util.List;
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

@SpringBootTest
@ActiveProfiles("test")
class SubscriptionIdempotencyConcurrencyIntegrationTests {

  @Autowired private SubscriptionService service;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MemberRepository members;
  @Autowired private ProductRepository products;
  @Autowired private SkuRepository skus;
  @Autowired private CategoryRepository categories;
  @Autowired private PasswordEncoder passwordEncoder;

  @AfterEach
  void cleanFixtures() {
    String memberFilter = "SELECT id FROM members WHERE email LIKE 'v2-concurrent-%@example.test'";
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
        "UPDATE subscription_plans SET current_plan_version_id=NULL WHERE name='DOG concurrent plan'");
    jdbc.update(
        "DELETE cycle FROM plan_version_delivery_cycles cycle JOIN plan_versions version ON"
            + " version.id=cycle.plan_version_id JOIN subscription_plans plan ON plan.id=version.plan_id"
            + " WHERE plan.name='DOG concurrent plan'");
    jdbc.update(
        "DELETE item FROM plan_items item JOIN plan_versions version ON version.id=item.plan_version_id"
            + " JOIN subscription_plans plan ON plan.id=version.plan_id WHERE plan.name='DOG concurrent plan'");
    jdbc.update(
        "DELETE version FROM plan_versions version JOIN subscription_plans plan ON"
            + " plan.id=version.plan_id WHERE plan.name='DOG concurrent plan'");
    jdbc.update("DELETE FROM subscription_plans WHERE name='DOG concurrent plan'");
    jdbc.update(
        "DELETE inventory FROM inventories inventory JOIN skus sku ON sku.id=inventory.sku_id JOIN"
            + " products product ON product.id=sku.product_id WHERE product.name='Subscription concurrent product'");
    jdbc.update(
        "DELETE sku FROM skus sku JOIN products product ON product.id=sku.product_id WHERE"
            + " product.name='Subscription concurrent product'");
    jdbc.update("DELETE FROM products WHERE name='Subscription concurrent product'");
    jdbc.update("DELETE FROM categories WHERE slug LIKE 'v2-concurrent-%'");
    jdbc.update("DELETE FROM members WHERE email LIKE 'v2-concurrent-%@example.test'");
  }

  @Test
  void concurrentSameCommandKeyReturnsOneSuccessAndOneReplay() throws Exception {
    Member member =
        members.saveAndFlush(
            new Member(
                "v2-concurrent-" + UUID.randomUUID() + "@example.test",
                passwordEncoder.encode("test-password")));
    String suffix = UUID.randomUUID().toString();
    Product product =
        new Product(
            categories.saveAndFlush(
                new Category("v2-concurrent-" + suffix, "v2-concurrent-" + suffix, 0, true)),
            "Subscription concurrent product",
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
                "v2-concurrent-sku-" + UUID.randomUUID(),
                new BigDecimal("12000.00"),
                true,
                1));
    jdbc.update(
        "INSERT INTO subscription_plans(name,target_pet_type,on_sale) VALUES (?,?,true)",
        "DOG concurrent plan",
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
    SubscriptionResult created =
        service.createSubscription(
            member.getId(),
            "concurrent-create",
            new CreateSubscriptionRequest(petId, planVersionId, 4));
    long subscriptionId = created.body().subscriptionId();

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Future<SubscriptionResult> first =
          executor.submit(
              () -> {
                start.await();
                return service.command(
                    member.getId(),
                    subscriptionId,
                    "pause",
                    "concurrent-pause",
                    "\"0\"",
                    SubscriptionCommandRequest.empty());
              });
      Future<SubscriptionResult> second =
          executor.submit(
              () -> {
                start.await();
                return service.command(
                    member.getId(),
                    subscriptionId,
                    "pause",
                    "concurrent-pause",
                    "\"0\"",
                    SubscriptionCommandRequest.empty());
              });
      start.countDown();

      List<SubscriptionResult> results =
          List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));

      assertThat(results)
          .extracting(SubscriptionResult::replay)
          .containsExactlyInAnyOrder(false, true);
      assertThat(results)
          .allSatisfy(
              result -> {
                assertThat(result.etag()).isEqualTo("\"1\"");
                assertThat(result.body().status()).isEqualTo("PAUSED");
                assertThat(result.body().version()).isEqualTo(1L);
              });
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM subscription_command_history WHERE subscription_id=? AND"
                      + " command_type='PAUSE'",
                  Integer.class,
                  subscriptionId))
          .isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }
}
