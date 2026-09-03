package com.pawcycle.backend.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawcycle.backend.subscription.api.CreatePetRequest;
import com.pawcycle.backend.subscription.api.CreateSubscriptionRequest;
import com.pawcycle.backend.subscription.api.SubscriptionCommandRequest;
import com.pawcycle.backend.subscription.api.SubscriptionDetailResponse;

import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.persistence.CategoryRepository;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.persistence.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.persistence.SkuRepository;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.persistence.MemberRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubscriptionCommandIntegrationTests {

  @Autowired private SubscriptionService service;
  @Autowired private SubscriptionOrderAutomationService automation;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MemberRepository members;
  @Autowired private ProductRepository products;
  @Autowired private SkuRepository skus;
  @Autowired private CategoryRepository categories;
  @Autowired private PasswordEncoder passwordEncoder;

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
                "v2-command-" + UUID.randomUUID() + "@example.test",
                passwordEncoder.encode("test-password")));
    product =
        products.saveAndFlush(
            new Product(activeCategory(), "Subscription command product", "test", null, "DOG", null));
    product.transitionTo(com.pawcycle.backend.catalog.product.domain.ProductStatus.PUBLIC);
    sku =
        skus.saveAndFlush(
            com.pawcycle.backend.support.TestSkuFactory.sku(
                product,
                "v2-command-sku-" + UUID.randomUUID(),
                new BigDecimal("12000.00"),
                true,
                1));
    jdbc.update(
        "INSERT INTO subscription_plans(name,target_pet_type,on_sale) VALUES (?,?,true)",
        "DOG command plan",
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
    enableCommerceFulfillment();
  }

  private Category activeCategory() {
    String suffix = UUID.randomUUID().toString();
    return categories.saveAndFlush(
        new Category("v2-command-" + suffix, "v2-command-" + suffix, 0, true));
  }

  private void enableCommerceFulfillment() {
    jdbc.update(
        "INSERT INTO inventories(sku_id,available_quantity,reserved_quantity,version) VALUES"
            + " (?,100,0,0) ON DUPLICATE KEY UPDATE available_quantity=100",
        sku.getId());
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
        "DELETE im FROM inventory_movements im JOIN payments p ON p.id=im.payment_id JOIN orders o"
            + " ON o.id=p.order_id JOIN subscription_order_context c ON c.order_id=o.id JOIN"
            + " subscriptions s ON s.id=c.subscription_id WHERE s.member_id=?",
        memberId);
    jdbc.update(
        "DELETE p FROM payments p JOIN orders o ON o.id=p.order_id JOIN subscription_order_context"
            + " c ON c.order_id=o.id JOIN subscriptions s ON s.id=c.subscription_id WHERE"
            + " s.member_id=?",
        memberId);
    jdbc.update(
        "DELETE oi FROM order_items oi JOIN subscription_order_context c ON c.order_id=oi.order_id"
            + " JOIN subscriptions s ON s.id=c.subscription_id WHERE s.member_id=?",
        memberId);
    jdbc.update(
        "DELETE c FROM subscription_order_context c JOIN subscriptions s ON s.id=c.subscription_id"
            + " WHERE s.member_id=?",
        memberId);
    jdbc.update("DELETE FROM orders WHERE member_id=? AND source='SUBSCRIPTION'", memberId);
    jdbc.update(
        "DELETE item FROM subscription_order_items item JOIN subscription_orders orders ON"
            + " orders.id=item.order_id JOIN subscriptions s ON s.id=orders.subscription_id WHERE"
            + " s.member_id=?",
        memberId);
    jdbc.update(
        "DELETE item FROM subscription_order_addon_items item JOIN subscription_orders orders ON"
            + " orders.id=item.subscription_order_id JOIN subscriptions s ON"
            + " s.id=orders.subscription_id WHERE s.member_id=?",
        memberId);
    jdbc.update(
        "DELETE orders FROM subscription_orders orders JOIN subscriptions s ON"
            + " s.id=orders.subscription_id WHERE s.member_id=?",
        memberId);
    jdbc.update(
        "DELETE addon FROM subscription_schedule_addons addon JOIN subscription_schedules schedule"
            + " ON schedule.id=addon.schedule_id JOIN subscriptions s ON"
            + " s.id=schedule.subscription_id WHERE s.member_id=?",
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
    jdbc.update(
        "DELETE sh FROM subscription_shipping_snapshots sh JOIN subscriptions s ON"
            + " s.id=sh.subscription_id WHERE s.member_id=?",
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
      jdbc.update("DELETE FROM inventories WHERE sku_id=?", sku.getId());
      jdbc.update("DELETE FROM skus WHERE id=?", sku.getId());
    }
    if (product != null) {
      jdbc.update("DELETE FROM products WHERE id=?", product.getId());
    }
    jdbc.update("UPDATE members SET default_address_id=NULL WHERE id=?", memberId);
    jdbc.update("DELETE FROM billing_payment_methods WHERE member_id=?", memberId);
    jdbc.update("DELETE FROM member_addresses WHERE member_id=?", memberId);
    jdbc.update("DELETE FROM members WHERE id=?", memberId);
  }

  @Test
  void commandRequiresIfMatch() {
    long subscriptionId = createSubscription("missing-if-match");

    assertThatThrownBy(
            () ->
                service.command(
                    member.getId(), subscriptionId, "pause", "missing-if-match", null, SubscriptionCommandRequest.empty()))
        .isInstanceOf(SubscriptionApiException.class)
        .hasFieldOrPropertyWithValue("code", "IF_MATCH_REQUIRED");
  }

  @Test
  void commandRejectsInvalidIfMatch() {
    long subscriptionId = createSubscription("invalid-if-match");

    assertThatThrownBy(
            () ->
                service.command(
                    member.getId(), subscriptionId, "pause", "invalid-if-match", "0", SubscriptionCommandRequest.empty()))
        .isInstanceOf(SubscriptionApiException.class)
        .hasFieldOrPropertyWithValue("code", "IF_MATCH_INVALID");
  }

  @Test
  void commandRejectsStaleVersion() {
    long subscriptionId = createSubscription("stale-if-match");

    assertThatThrownBy(
            () ->
                service.command(
                    member.getId(), subscriptionId, "pause", "stale-if-match", "\"1\"", SubscriptionCommandRequest.empty()))
        .isInstanceOf(SubscriptionApiException.class)
        .hasFieldOrPropertyWithValue("code", "SUBSCRIPTION_VERSION_MISMATCH");
  }

  @Test
  void changePlanKeepsCurrentCycleAndCreatesPendingSnapshot() {
    long subscriptionId = createSubscription("change-plan");

    SubscriptionCommandRequest commandBody =
        new SubscriptionCommandRequest(null, planVersionId, null, null, null, null);
    SubscriptionResult result =
        service.command(
            member.getId(), subscriptionId, "change-plan", "change-plan", "\"0\"", commandBody);
    assertThat(result.etag()).isEqualTo("\"1\"");
    assertThat(result.body().version()).isEqualTo(1L);
    assertThat(result.body().pendingSnapshot()).isNotNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM pending_plan_changes WHERE subscription_id=?",
                Integer.class,
                subscriptionId))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT ss.delivery_cycle_weeks FROM pending_plan_changes p JOIN"
                    + " subscription_snapshots ss ON ss.id=p.snapshot_id WHERE p.subscription_id=?",
                Integer.class,
                subscriptionId))
        .isEqualTo(4);

    LocalDateTime completedAt =
        jdbc.queryForObject(
            "SELECT completed_at FROM subscription_command_idempotency_results WHERE member_id=?"
                + " AND subscription_id=? AND command_type=? AND idempotency_key=?",
            LocalDateTime.class,
            member.getId(),
            subscriptionId,
            "CHANGE_PLAN",
            "change-plan");
    assertThat(
            jdbc.update(
                "UPDATE subscription_command_idempotency_results SET"
                    + " response_body=JSON_SET(response_body,'$.currentSnapshot.snapshotId',9001,'$.pendingSnapshot.snapshotId',9002)"
                    + " WHERE member_id=? AND subscription_id=? AND command_type=? AND"
                    + " idempotency_key=?",
                member.getId(),
                subscriptionId,
                "CHANGE_PLAN",
                "change-plan"))
        .isEqualTo(1);
    SubscriptionResult replay =
        service.command(
            member.getId(), subscriptionId, "change-plan", "change-plan", "\"999\"", commandBody);
    assertThat(completedAt).isNotNull();
    assertThat(replay.replay()).isTrue();
    assertThat(replay.body().currentSnapshot()).isNotNull();
    assertThat(replay.body().pendingSnapshot()).isNotNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT response_body FROM subscription_command_idempotency_results WHERE"
                    + " member_id=? AND subscription_id=? AND command_type=? AND idempotency_key=?",
                String.class,
                member.getId(),
                subscriptionId,
                "CHANGE_PLAN",
                "change-plan"))
        .doesNotContain("\"snapshotId\"");
    assertThat(
            jdbc.queryForObject(
                "SELECT completed_at FROM subscription_command_idempotency_results WHERE"
                    + " member_id=? AND subscription_id=? AND command_type=? AND idempotency_key=?",
                LocalDateTime.class,
                member.getId(),
                subscriptionId,
                "CHANGE_PLAN",
                "change-plan"))
        .isEqualTo(completedAt);
  }

  @Test
  void planAndCycleChangesComposeInBothOrdersAndKeepOnePendingSnapshot() {
    jdbc.update(
        "INSERT INTO plan_version_delivery_cycles(plan_version_id,delivery_cycle_weeks) VALUES"
            + " (?,8)",
        planVersionId);
    long alternateVersionId = createAlternatePlanVersion("compose", List.of(4, 8));

    long planFirst = createSubscription("plan-first");
    service.command(
        member.getId(),
        planFirst,
        "change-plan",
        "plan-first-plan",
        "\"0\"",
        new SubscriptionCommandRequest(null, alternateVersionId, null, null, null, null));
    service.command(
        member.getId(),
        planFirst,
        "change-delivery-cycle",
        "plan-first-cycle",
        "\"1\"",
        new SubscriptionCommandRequest(null, null, 8, null, null, null));

    assertPendingSnapshot(planFirst, alternateVersionId, 8);
    assertThat(
            jdbc.queryForObject(
                "SELECT delivery_cycle_weeks FROM subscriptions WHERE id=?",
                Integer.class,
                planFirst))
        .isEqualTo(4);

    long cycleFirst = createSubscription("cycle-first");
    service.command(
        member.getId(),
        cycleFirst,
        "change-delivery-cycle",
        "cycle-first-cycle",
        "\"0\"",
        new SubscriptionCommandRequest(null, null, 8, null, null, null));
    service.command(
        member.getId(),
        cycleFirst,
        "change-plan",
        "cycle-first-plan",
        "\"1\"",
        new SubscriptionCommandRequest(null, alternateVersionId, null, null, null, null));

    assertPendingSnapshot(cycleFirst, alternateVersionId, 8);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM pending_plan_changes WHERE subscription_id IN (?,?)",
                Integer.class,
                planFirst,
                cycleFirst))
        .isEqualTo(2);
  }

  @Test
  void cycleChangeUsesPendingPlanSupportAndRejectsUnsupportedCycle() {
    jdbc.update(
        "INSERT INTO plan_version_delivery_cycles(plan_version_id,delivery_cycle_weeks) VALUES"
            + " (?,8)",
        planVersionId);
    long fourWeekOnlyVersionId = createAlternatePlanVersion("four-week-only", List.of(4));
    long subscriptionId = createSubscription("pending-plan-cycle-validation");
    service.command(
        member.getId(),
        subscriptionId,
        "change-plan",
        "pending-plan",
        "\"0\"",
        new SubscriptionCommandRequest(null, fourWeekOnlyVersionId, null, null, null, null));

    assertThatThrownBy(
            () ->
                service.command(
                    member.getId(),
                    subscriptionId,
                    "change-delivery-cycle",
                    "unsupported-pending-cycle",
                    "\"1\"",
                    new SubscriptionCommandRequest(null, null, 8, null, null, null)))
        .isInstanceOf(SubscriptionApiException.class)
        .hasFieldOrPropertyWithValue("code", "DELIVERY_CYCLE_NOT_ALLOWED");
  }

  @Test
  void pendingCycleSurvivesSkipPauseResumeAndCancelRemovesIt() {
    jdbc.update(
        "INSERT INTO plan_version_delivery_cycles(plan_version_id,delivery_cycle_weeks) VALUES"
            + " (?,8)",
        planVersionId);
    long subscriptionId = createSubscription("cycle-state-commands");
    service.command(
        member.getId(),
        subscriptionId,
        "change-delivery-cycle",
        "cycle-before-state",
        "\"0\"",
        new SubscriptionCommandRequest(null, null, 8, null, null, null));
    long originalTarget =
        jdbc.queryForObject(
            "SELECT target_schedule_id FROM pending_plan_changes WHERE subscription_id=?",
            Long.class,
            subscriptionId);

    service.command(
        member.getId(), subscriptionId, "skip-next", "skip-with-cycle", "\"1\"", SubscriptionCommandRequest.empty());
    long skippedTarget =
        jdbc.queryForObject(
            "SELECT target_schedule_id FROM pending_plan_changes WHERE subscription_id=?",
            Long.class,
            subscriptionId);
    assertThat(skippedTarget).isNotEqualTo(originalTarget);
    service.command(member.getId(), subscriptionId, "pause", "pause-with-cycle", "\"2\"", SubscriptionCommandRequest.empty());
    service.command(
        member.getId(), subscriptionId, "resume", "resume-with-cycle", "\"3\"", SubscriptionCommandRequest.empty());

    assertPendingSnapshot(subscriptionId, planVersionId, 8);
    assertThat(
            jdbc.queryForObject(
                "SELECT target_schedule_id FROM pending_plan_changes WHERE subscription_id=?",
                Long.class,
                subscriptionId))
        .isEqualTo(skippedTarget);
    assertThat(
            jdbc.queryForObject(
                "SELECT delivery_cycle_weeks FROM subscriptions WHERE id=?",
                Integer.class,
                subscriptionId))
        .isEqualTo(4);

    service.command(
        member.getId(), subscriptionId, "cancel", "cancel-with-cycle", "\"4\"", SubscriptionCommandRequest.empty());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM pending_plan_changes WHERE subscription_id=?",
                Integer.class,
                subscriptionId))
        .isZero();
  }

  @Test
  void reschedulePreservesScheduleIdAndSupportsReplayBeforeStaleIfMatch() {
    long subscriptionId = createSubscription("reschedule");
    long scheduleId =
        jdbc.queryForObject(
            "SELECT id FROM subscription_schedules WHERE subscription_id=? AND status='SCHEDULED'",
            Long.class,
            subscriptionId);
    LocalDate requestedDate = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(10);
    SubscriptionCommandRequest body =
        new SubscriptionCommandRequest(null, null, null, requestedDate, null, null);

    SubscriptionResult first =
        service.command(
            member.getId(), subscriptionId, "reschedule-next", "reschedule-key", "\"0\"", body);
    SubscriptionResult replay =
        service.command(
            member.getId(), subscriptionId, "reschedule-next", "reschedule-key", null, body);

    assertThat(first.etag()).isEqualTo("\"1\"");
    assertThat(replay.replay()).isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT id FROM subscription_schedules WHERE subscription_id=? AND"
                    + " status='SCHEDULED'",
                Long.class,
                subscriptionId))
        .isEqualTo(scheduleId);
    assertThat(
            jdbc.queryForObject(
                "SELECT scheduled_date FROM subscription_schedules WHERE id=?",
                LocalDate.class,
                scheduleId))
        .isEqualTo(requestedDate);
    assertThatThrownBy(
            () ->
                service.command(
                    member.getId(),
                    subscriptionId,
                    "reschedule-next",
                    "stale-reschedule",
                    "\"0\"",
                    new SubscriptionCommandRequest(
                        null, null, null, requestedDate.plusDays(1), null, null)))
        .isInstanceOf(SubscriptionApiException.class)
        .hasFieldOrPropertyWithValue("code", "SUBSCRIPTION_VERSION_MISMATCH");
  }

  @Test
  void rescheduleRejectsTodayAndExistingScheduleDate() {
    long subscriptionId = createSubscription("reschedule-validation");
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    LocalDate existingDate = today.plusDays(20);
    jdbc.update(
        "INSERT INTO"
            + " subscription_schedules(subscription_id,scheduled_date,status,effective_snapshot_id)"
            + " VALUES (?,?,'SKIPPED',NULL)",
        subscriptionId,
        existingDate);

    assertThatThrownBy(
            () ->
                service.command(
                    member.getId(),
                    subscriptionId,
                    "reschedule-next",
                    "today-reschedule",
                    "\"0\"",
                    new SubscriptionCommandRequest(null, null, null, today, null, null)))
        .isInstanceOf(SubscriptionApiException.class)
        .hasFieldOrPropertyWithValue("code", "SCHEDULE_DATE_NOT_FUTURE");
    assertThatThrownBy(
            () ->
                service.command(
                    member.getId(),
                    subscriptionId,
                    "reschedule-next",
                    "duplicate-reschedule",
                    "\"0\"",
                    new SubscriptionCommandRequest(null, null, null, existingDate, null, null)))
        .isInstanceOf(SubscriptionApiException.class)
        .hasFieldOrPropertyWithValue("code", "SCHEDULE_DATE_CONFLICT");
    assertThatThrownBy(
            () ->
                service.command(
                    member.getId(),
                    subscriptionId,
                    "reschedule-next",
                    "invalid-calendar-reschedule",
                    "\"0\"",
                    SubscriptionCommandRequest.empty()))
        .isInstanceOf(SubscriptionApiException.class)
        .hasFieldOrPropertyWithValue("code", "VALIDATION_FAILED");
    assertThatThrownBy(
            () ->
                service.command(
                    member.getId(),
                    subscriptionId,
                    "reschedule-next",
                    "non-string-reschedule",
                    "\"0\"",
                    SubscriptionCommandRequest.empty()))
        .isInstanceOf(SubscriptionApiException.class)
        .hasFieldOrPropertyWithValue("code", "VALIDATION_FAILED");
  }

  @Test
  void detailAddsUserProjectionWithProductIssueAndServerActions() {
    jdbc.update(
        "UPDATE products SET thumbnail_url='https://cdn.example.test/product.png' WHERE id=?",
        product.getId());
    jdbc.update(
        "INSERT INTO plan_version_delivery_cycles(plan_version_id,delivery_cycle_weeks) VALUES"
            + " (?,8)",
        planVersionId);
    long subscriptionId = createSubscription("detail-projection");
    service.command(
        member.getId(),
        subscriptionId,
        "change-delivery-cycle",
        "detail-pending",
        "\"0\"",
        new SubscriptionCommandRequest(null, null, 8, null, null, null));

    SubscriptionDetailResponse detail =
        service.subscription(member.getId(), subscriptionId, 0, 20, 0, 20).body();
    var nextDelivery = detail.nextDelivery();
    var pendingChange = detail.pendingChange();
    assertThat(nextDelivery).isNotNull();
    assertThat(nextDelivery.items()).isNotEmpty();
    var item = nextDelivery.items().getFirst();
    assertThat(item.productName()).isEqualTo("Subscription command product");
    assertThat(item.thumbnailUrl()).isEqualTo("https://cdn.example.test/product.png");
    assertThat(pendingChange).isNotNull();
    assertThat(pendingChange.deliveryCycleWeeks()).isEqualTo(8);
    assertThat(pendingChange.appliesOn()).isNotNull();
    assertThat(detail.availableActions())
        .containsExactly(
            "CHANGE_PLAN",
            "CHANGE_DELIVERY_CYCLE",
            "RESCHEDULE_NEXT",
            "SKIP_NEXT",
            "PAUSE",
            "SET_NEXT_DELIVERY_ADDON",
            "CANCEL",
            "UPDATE_SHIPPING_ADDRESS");

    jdbc.update(
        "UPDATE subscription_schedules SET status='HELD',hold_reason='MISSING_BILLING_METHOD' WHERE"
            + " subscription_id=? AND status='SCHEDULED'",
        subscriptionId);
    SubscriptionDetailResponse heldDetail =
        service.subscription(member.getId(), subscriptionId, 0, 20, 0, 20).body();
    assertThat(heldDetail.issue()).isNotNull();
    assertThat(heldDetail.issue().code()).isEqualTo("BILLING_METHOD_REQUIRED");
    assertThat(heldDetail.issue().message()).doesNotContain("MISSING_BILLING_METHOD");
    assertThat(heldDetail.availableActions())
        .containsExactly("REGISTER_BILLING_METHOD", "CANCEL");

    jdbc.update(
        "UPDATE subscription_schedules SET hold_reason='MISSING_SHIPPING_ADDRESS' WHERE"
            + " subscription_id=? AND status='HELD'",
        subscriptionId);
    SubscriptionDetailResponse shippingHeldDetail =
        service.subscription(member.getId(), subscriptionId, 0, 20, 0, 20).body();
    assertThat(shippingHeldDetail.issue()).isNotNull();
    assertThat(shippingHeldDetail.issue().code()).isEqualTo("SHIPPING_ADDRESS_REQUIRED");
    assertThat(shippingHeldDetail.issue().message()).doesNotContain("MISSING_SHIPPING_ADDRESS");
    assertThat(shippingHeldDetail.availableActions())
        .containsExactly("UPDATE_SHIPPING_ADDRESS", "CANCEL");
  }

  @Test
  void skipNextMarksCurrentScheduleAndCreatesReplacement() {
    long subscriptionId = createSubscription("skip-next");

    SubscriptionResult result =
        service.command(
            member.getId(), subscriptionId, "skip-next", "skip-next", "\"0\"", SubscriptionCommandRequest.empty());

    assertThat(result.etag()).isEqualTo("\"1\"");
    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM subscriptions WHERE id=?", Long.class, subscriptionId))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND"
                    + " status='SKIPPED'",
                Integer.class,
                subscriptionId))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND"
                    + " status='SCHEDULED'",
                Integer.class,
                subscriptionId))
        .isEqualTo(1);
  }

  @Test
  void skipPauseAndResumeAvoidScheduleDateCollision() {
    long subscriptionId = createSubscription("skip-pause-resume");
    service.command(
        member.getId(), subscriptionId, "skip-next", "skip-before-pause", "\"0\"", SubscriptionCommandRequest.empty());
    service.command(member.getId(), subscriptionId, "pause", "pause-after-skip", "\"1\"", SubscriptionCommandRequest.empty());

    SubscriptionResult resumed =
        service.command(
            member.getId(), subscriptionId, "resume", "resume-after-skip", "\"2\"", SubscriptionCommandRequest.empty());

    assertThat(resumed.body().status()).isEqualTo("ACTIVE");
    assertThat(resumed.body().version()).isEqualTo(3L);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(DISTINCT scheduled_date) FROM subscription_schedules WHERE"
                    + " subscription_id=?",
                Integer.class,
                subscriptionId))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND"
                    + " status='SKIPPED'",
                Integer.class,
                subscriptionId))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND"
                    + " status='SCHEDULED'",
                Integer.class,
                subscriptionId))
        .isEqualTo(1);
  }

  @Test
  void pauseReplayAndResumePreserveVersionContract() {
    long subscriptionId = createSubscription("pause-resume");

    SubscriptionResult paused =
        service.command(member.getId(), subscriptionId, "pause", "pause-replay", "\"0\"", SubscriptionCommandRequest.empty());
    SubscriptionResult replay =
        service.command(member.getId(), subscriptionId, "pause", "pause-replay", null, SubscriptionCommandRequest.empty());

    assertThat(paused.etag()).isEqualTo("\"1\"");
    assertThat(paused.body().status()).isEqualTo("PAUSED");
    assertThat(paused.body().version()).isEqualTo(1L);
    assertThat(replay.replay()).isTrue();
    assertThat(replay.etag()).isEqualTo("\"1\"");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND"
                    + " status='HELD'",
                Integer.class,
                subscriptionId))
        .isEqualTo(1);

    SubscriptionResult resumed =
        service.command(
            member.getId(), subscriptionId, "resume", "resume-after-pause", "\"1\"", SubscriptionCommandRequest.empty());

    assertThat(resumed.etag()).isEqualTo("\"2\"");
    assertThat(resumed.body().status()).isEqualTo("ACTIVE");
    assertThat(resumed.body().version()).isEqualTo(2L);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND"
                    + " status='SCHEDULED'",
                Integer.class,
                subscriptionId))
        .isEqualTo(1);
  }

  @Test
  void overduePauseDoesNotConsumeScheduleWithoutOrder() {
    long subscriptionId = createSubscription("overdue-command");
    jdbc.update(
        "UPDATE subscription_schedules SET scheduled_date=? WHERE subscription_id=?",
        LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1),
        subscriptionId);

    SubscriptionResult paused =
        service.command(
                    member.getId(), subscriptionId, "pause", "overdue-pause", "\"0\"", SubscriptionCommandRequest.empty());
    assertThat(paused.body().status()).isEqualTo("PAUSED");
    assertThat(paused.body().version()).isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM subscriptions WHERE id=?", Long.class, subscriptionId))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_orders WHERE subscription_id=?",
                Integer.class,
                subscriptionId))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND"
                    + " status='HELD' AND effective_snapshot_id IS NULL",
                Integer.class,
                subscriptionId))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND"
                    + " status='SCHEDULED'",
                Integer.class,
                subscriptionId))
        .isZero();
  }

  @Test
  void cancelRemovesPendingChangeAndCancelsFutureSchedules() {
    long subscriptionId = createSubscription("cancel");
    service.command(
        member.getId(),
        subscriptionId,
        "change-plan",
        "change-before-cancel",
        "\"0\"",
        new SubscriptionCommandRequest(null, planVersionId, null, null, null, null));

    SubscriptionResult canceled =
    service.command(member.getId(), subscriptionId, "cancel", "cancel", "\"1\"", SubscriptionCommandRequest.empty());

    assertThat(canceled.etag()).isEqualTo("\"2\"");
    assertThat(canceled.body().status()).isEqualTo("CANCELED");
    assertThat(canceled.body().version()).isEqualTo(2L);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM pending_plan_changes WHERE subscription_id=?",
                Integer.class,
                subscriptionId))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND"
                    + " status='CANCELED'",
                Integer.class,
                subscriptionId))
        .isEqualTo(1);
  }

  @Test
  void commandNormalizationIsIndependentOfDefaultLocale() {
    long subscriptionId = createSubscription("locale");
    Locale previous = Locale.getDefault();

    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      SubscriptionResult result =
          service.command(
              member.getId(), subscriptionId, "skip-next", "locale-skip", "\"0\"", SubscriptionCommandRequest.empty());
      assertThat(result.etag()).isEqualTo("\"1\"");
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND"
                      + " status='SKIPPED'",
                  Integer.class,
                  subscriptionId))
          .isEqualTo(1);
    } finally {
      Locale.setDefault(previous);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void scheduleDtoIncludesNullableAndAppliedEffectiveSnapshotId() {
    long subscriptionId = createSubscription("schedule-dto");
    SubscriptionResult initial =
        service.subscription(member.getId(), subscriptionId, 0, 20, 0, 20);
    var initialSchedule = initial.body().schedules().items().getFirst();
    assertThat(initialSchedule.effectiveSnapshotId()).isNull();

    jdbc.update(
        "UPDATE subscription_schedules SET scheduled_date=? WHERE subscription_id=?",
        LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1),
        subscriptionId);

    SubscriptionAutomationBatchResult processed = automation.processDueSchedules(10);

    SubscriptionResult reconciled =
        service.subscription(member.getId(), subscriptionId, 0, 20, 0, 20);

    assertThat(reconciled.body().schedules().items())
        .anySatisfy(item -> assertThat(item.effectiveSnapshotId()).isNotNull());
    assertThat(processed.ordersCreated()).isGreaterThanOrEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_orders WHERE subscription_id=?",
                Integer.class,
                subscriptionId))
        .isEqualTo(1);
  }

  @Test
  void planVersionDistinguishesMismatchAndUnavailableStates() {
    long dogPetId = createPet("dog-plan", "DOG");
    long catPetId = createPet("cat-plan", "CAT");
    long planId =
        jdbc.queryForObject(
            "SELECT plan_id FROM plan_versions WHERE id=?", Long.class, planVersionId);
    jdbc.update(
        "INSERT INTO plan_versions(plan_id,package_price_krw,is_migration_only) VALUES"
            + " (?,26000,false)",
        planId);
    long previousVersionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

    assertThatThrownBy(() -> service.planVersion(member.getId(), dogPetId, previousVersionId))
        .isInstanceOf(SubscriptionApiException.class)
        .hasFieldOrPropertyWithValue("code", "PLAN_NOT_AVAILABLE");
    assertThatThrownBy(() -> service.planVersion(member.getId(), catPetId, planVersionId))
        .isInstanceOf(SubscriptionApiException.class)
        .hasFieldOrPropertyWithValue("code", "PLAN_PET_TYPE_MISMATCH");

    jdbc.update("UPDATE subscription_plans SET current_plan_version_id=NULL WHERE id=?", planId);
    assertThatThrownBy(() -> service.planVersion(member.getId(), dogPetId, planVersionId))
        .isInstanceOf(SubscriptionApiException.class)
        .hasFieldOrPropertyWithValue("code", "PLAN_NOT_AVAILABLE");
  }

  @Test
  void invalidPetIdIsRejectedWithoutWrapping() {
    long petId = createPet("invalid-pet-id", "DOG");
    assertThatThrownBy(
            () ->
                service.createSubscription(
                    member.getId(),
                    "invalid-pet-id",
                    new CreateSubscriptionRequest(-1L, planVersionId, 4)))
        .isInstanceOf(SubscriptionApiException.class)
        .hasFieldOrPropertyWithValue("code", "VALIDATION_FAILED");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscriptions WHERE member_id=? AND pet_id=?",
                Integer.class,
                member.getId(),
                petId))
        .isZero();
  }

  @Test
  void idempotencyKeysAreCaseSensitive() {
    long petId = createPet("case-key", "DOG");
    CreateSubscriptionRequest body = new CreateSubscriptionRequest(petId, planVersionId, 4);
    SubscriptionResult upper =
        service.createSubscription(member.getId(), "CaseKey", body);
    SubscriptionResult lower =
        service.createSubscription(member.getId(), "casekey", body);
    assertThat(upper.body().subscriptionId()).isNotEqualTo(lower.body().subscriptionId());
  }

  private long createSubscription(String key) {
    long petId = createPet(key, "DOG");
    SubscriptionResult created =
        service.createSubscription(
            member.getId(),
            "create-" + key,
            new CreateSubscriptionRequest(petId, planVersionId, 4));
    return created.body().subscriptionId();
  }

  private long createPet(String key, String petType) {
    return service
                .createPet(member.getId(), new CreatePetRequest("반려동물-" + key, petType)).petId();
  }

  private long createAlternatePlanVersion(String key, List<Integer> cycles) {
    jdbc.update(
        "INSERT INTO subscription_plans(name,target_pet_type,on_sale) VALUES (?,?,true)",
        "alternate-" + key,
        "DOG");
    long alternatePlanId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update(
        "INSERT INTO plan_versions(plan_id,package_price_krw,is_migration_only) VALUES"
            + " (?,26000,false)",
        alternatePlanId);
    long alternateVersionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update(
        "INSERT INTO plan_items(plan_version_id,sku_id,quantity) VALUES (?,?,2)",
        alternateVersionId,
        sku.getId());
    for (Integer cycle : cycles)
      jdbc.update(
          "INSERT INTO plan_version_delivery_cycles(plan_version_id,delivery_cycle_weeks) VALUES"
              + " (?,?)",
          alternateVersionId,
          cycle);
    jdbc.update(
        "UPDATE subscription_plans SET current_plan_version_id=? WHERE id=?",
        alternateVersionId,
        alternatePlanId);
    return alternateVersionId;
  }

  private void assertPendingSnapshot(
      long subscriptionId, long expectedPlanVersionId, int expectedCycle) {
    Map<String, Object> pending =
        jdbc.queryForMap(
            "SELECT snapshot.source_plan_version_id,snapshot.delivery_cycle_weeks FROM"
                + " pending_plan_changes pending JOIN subscription_snapshots snapshot ON"
                + " snapshot.id=pending.snapshot_id WHERE pending.subscription_id=?",
            subscriptionId);
    assertThat(pending)
        .containsEntry("SOURCE_PLAN_VERSION_ID", expectedPlanVersionId)
        .containsEntry("DELIVERY_CYCLE_WEEKS", expectedCycle);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM pending_plan_changes WHERE subscription_id=?",
                Integer.class,
                subscriptionId))
        .isEqualTo(1);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> castMap(Object value) {
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private List<Object> castList(Object value) {
    return (List<Object>) value;
  }
}
