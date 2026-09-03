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
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.persistence.MemberRepository;
import com.pawcycle.backend.subscription.api.CreatePetRequest;
import com.pawcycle.backend.subscription.api.CreateSubscriptionRequest;
import com.pawcycle.backend.subscription.api.SubscriptionCommandRequest;
import com.pawcycle.backend.support.TransactionalTestSql;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Import(SubscriptionOrderAutomationServiceIntegrationTests.FixedClockConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class SubscriptionOrderAutomationServiceIntegrationTests {

  private static final LocalDate TODAY = LocalDate.of(2026, 8, 1);
  private static final Instant NOW = Instant.parse("2026-07-31T15:00:00Z");
  private static final String EMAIL_PREFIX = "sub-auto-001-";
  private static final String PRODUCT_PREFIX = "SUB-AUTO-001 product ";
  private static final String PLAN_PREFIX = "SUB-AUTO-001 plan ";

  @Autowired private SubscriptionOrderAutomationService automation;
  @Autowired private SubscriptionService subscriptions;
  @Autowired private MemberRepository members;
  @Autowired private ProductRepository products;
  @Autowired private CategoryRepository categories;
  @Autowired private SkuRepository skus;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private MeterRegistry meterRegistry;
  @Autowired private ApplicationContext applicationContext;
  @Autowired private PlatformTransactionManager transactionManager;
  @MockitoSpyBean private NativeQueryExecutor nativeJdbc;

  private TransactionalTestSql jdbc;
  private Member member;
  private Product product;
  private Sku firstSku;
  private Sku secondSku;
  private long basePlanVersionId;
  private long alternatePlanVersionId;

  @BeforeEach
  void setUp() {
    jdbc = new TransactionalTestSql(nativeJdbc, transactionManager);
    cleanFixtures();
    String suffix = UUID.randomUUID().toString();
    member =
        members.saveAndFlush(
            new Member(
                EMAIL_PREFIX + suffix + "@example.test", passwordEncoder.encode("test-password")));
    jdbc.update(
        "INSERT INTO"
            + " member_addresses(member_id,name,recipient_name,recipient_phone,postal_code,address_line1,address_line2,created_at,updated_at)"
            + " VALUES (?,?,'recipient','010-0000-0000','00000','address',NULL,?,?)",
        member.getId(),
        "address-" + suffix,
        LocalDateTime.now(),
        LocalDateTime.now());
    long addressId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update("UPDATE members SET default_address_id=? WHERE id=?", addressId, member.getId());
    jdbc.update(
        "INSERT INTO"
            + " billing_payment_methods(member_id,provider,customer_key,billing_key,status,created_at)"
            + " VALUES (?,'TOSS',?,?, 'ACTIVE',?)",
        member.getId(),
        "customer-" + suffix,
        "billing-" + suffix,
        LocalDateTime.now());
    Category category =
        categories.saveAndFlush(new Category("test-" + suffix, "sub-auto-" + suffix, 0, true));
    Product createdProduct =
        new Product(category, PRODUCT_PREFIX + suffix, "test", null, "DOG", null);
    createdProduct.transitionTo(com.pawcycle.backend.catalog.product.domain.ProductStatus.PUBLIC);
    product = products.saveAndFlush(createdProduct);
    firstSku =
        skus.saveAndFlush(
            com.pawcycle.backend.support.TestSkuFactory.sku(
                product, "sub-auto-first-" + suffix, new BigDecimal("12000.00"), true, 1));
    secondSku =
        skus.saveAndFlush(
            com.pawcycle.backend.support.TestSkuFactory.sku(
                product, "sub-auto-second-" + suffix, new BigDecimal("11000.00"), true, 2));
    jdbc.update(
        "INSERT INTO inventories(sku_id,available_quantity,reserved_quantity,version) VALUES"
            + " (?,100,0,0),(?,100,0,0)",
        firstSku.getId(),
        secondSku.getId());
    basePlanVersionId = createPlanVersion("base-" + suffix, 24000, firstSku.getId(), 2);
    alternatePlanVersionId = createPlanVersion("alternate-" + suffix, 33000, secondSku.getId(), 3);
  }

  @AfterEach
  void tearDown() {
    reset(nativeJdbc);
    cleanFixtures();
  }

  @Test
  void dueTodayCreatesOneImmutableOrderAndOneFutureScheduleAndRerunIsSafe() {
    long subscriptionId = createSubscription("due-today", basePlanVersionId, 2);
    long scheduleId = moveOnlyUnprocessedSchedule(subscriptionId, TODAY);
    long currentSnapshotId = currentSnapshotId(subscriptionId);
    double executionsBefore = counter("pawcycle.subscription.automation.executions");
    double processedBefore = counter("pawcycle.subscription.automation.processed.candidates");
    double createdBefore = counter("pawcycle.subscription.automation.orders.created");
    long durationBefore =
        meterRegistry.get("pawcycle.subscription.automation.duration").timer().count();

    SubscriptionAutomationBatchResult first = automation.processDueSchedules(10);
    SubscriptionAutomationBatchResult second = automation.processDueSchedules(10);

    assertThat(first).isEqualTo(new SubscriptionAutomationBatchResult(1, 1, 0, 0));
    assertThat(second).isEqualTo(new SubscriptionAutomationBatchResult(0, 0, 0, 0));
    Map<String, Object> order =
        jdbc.queryForMap(
            "SELECT subscription_id,schedule_id,effective_snapshot_id,source_plan_version_id,"
                + "scheduled_date,processed_at,package_total_krw,status "
                + "FROM subscription_orders WHERE schedule_id=?",
            scheduleId);
    assertThat(((Number) order.get("SUBSCRIPTION_ID")).longValue()).isEqualTo(subscriptionId);
    assertThat(((Number) order.get("EFFECTIVE_SNAPSHOT_ID")).longValue())
        .isEqualTo(currentSnapshotId);
    assertThat(((Number) order.get("SOURCE_PLAN_VERSION_ID")).longValue())
        .isEqualTo(basePlanVersionId);
    assertThat(order.get("SCHEDULED_DATE")).isEqualTo(java.sql.Date.valueOf(TODAY));
    assertThat(order.get("PROCESSED_AT")).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
    assertThat(order.get("PACKAGE_TOTAL_KRW")).isEqualTo(new BigDecimal("24000.00"));
    assertThat(order.get("STATUS")).isEqualTo("CREATED");
    long orderId =
        jdbc.queryForObject(
            "SELECT id FROM subscription_orders WHERE schedule_id=?", Long.class, scheduleId);
    assertThat(
            jdbc.queryForMap(
                "SELECT sku_id,quantity FROM subscription_order_items WHERE order_id=?", orderId))
        .containsEntry("SKU_ID", firstSku.getId())
        .containsEntry("QUANTITY", 2);
    assertThat(
            jdbc.queryForObject(
                "SELECT effective_snapshot_id FROM subscription_schedules WHERE id=?",
                Long.class,
                scheduleId))
        .isEqualTo(currentSnapshotId);
    assertThat(futureScheduledDates(subscriptionId)).containsExactly(LocalDate.of(2026, 8, 15));
    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM subscriptions WHERE id=?", Long.class, subscriptionId))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_orders WHERE schedule_id=?",
                Integer.class,
                scheduleId))
        .isEqualTo(1);
    assertThat(counter("pawcycle.subscription.automation.executions"))
        .isEqualTo(executionsBefore + 2);
    assertThat(counter("pawcycle.subscription.automation.processed.candidates"))
        .isEqualTo(processedBefore + 1);
    assertThat(counter("pawcycle.subscription.automation.orders.created"))
        .isEqualTo(createdBefore + 1);
    assertThat(meterRegistry.get("pawcycle.subscription.automation.duration").timer().count())
        .isEqualTo(durationBefore + 2);
    assertThat(applicationContext.getBeansOfType(SubscriptionOrderAutomationTrigger.class))
        .isEmpty();
  }

  @Test
  void addOnUsesSubscriptionOrderParentAndPreservesDecimalSnapshotAcrossCommonOrderAndHistory() {
    jdbc.update(
        "UPDATE skus SET price=? WHERE id=?", new BigDecimal("11000.55"), secondSku.getId());
    long subscriptionId = createSubscription("decimal-addon", basePlanVersionId, 2);
    subscriptions.command(
        member.getId(),
        subscriptionId,
        "set-next-delivery-addon",
        "decimal-addon",
        "\"0\"",
        new SubscriptionCommandRequest(null, null, null, null, secondSku.getId(), 2));
    long scheduleId = moveOnlyUnprocessedSchedule(subscriptionId, TODAY);

    SubscriptionAutomationBatchResult result = automation.processDueSchedules(10);

    assertThat(result.ordersCreated()).isEqualTo(1);
    long subscriptionOrderId =
        jdbc.queryForObject(
            "SELECT id FROM subscription_orders WHERE schedule_id=?", Long.class, scheduleId);
    long commonOrderId =
        jdbc.queryForObject(
            "SELECT order_id FROM subscription_order_context WHERE schedule_id=?",
            Long.class,
            scheduleId);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_order_items WHERE order_id=?",
                Integer.class,
                subscriptionOrderId))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_items WHERE order_id=?", Integer.class, commonOrderId))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForMap(
                "SELECT quantity,unit_price_krw FROM subscription_order_addon_items WHERE"
                    + " subscription_order_id=? AND sku_id=?",
                subscriptionOrderId,
                secondSku.getId()))
        .containsEntry("QUANTITY", 2)
        .containsEntry("UNIT_PRICE_KRW", new BigDecimal("11000.55"));
    assertThat(
            jdbc.queryForObject(
                "SELECT package_total_krw FROM subscription_orders WHERE id=?",
                BigDecimal.class,
                subscriptionOrderId))
        .isEqualByComparingTo("46001.10");
    assertThat(
            jdbc.queryForObject(
                "SELECT payment_amount FROM orders WHERE id=?", BigDecimal.class, commonOrderId))
        .isEqualByComparingTo("46001.10");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_schedule_addons WHERE schedule_id=?",
                Integer.class,
                scheduleId))
        .isZero();
  }

  @Test
  void stockUnavailableAddOnIsRecoverableHeldAndCanBeRemovedBeforeRetry() {
    long subscriptionId = createSubscription("held-addon", basePlanVersionId, 2);
    subscriptions.command(
        member.getId(),
        subscriptionId,
        "set-next-delivery-addon",
        "held-addon",
        "\"0\"",
        new SubscriptionCommandRequest(null, null, null, null, secondSku.getId(), 1));
    long scheduleId = moveOnlyUnprocessedSchedule(subscriptionId, TODAY);
    jdbc.update("UPDATE inventories SET available_quantity=0 WHERE sku_id=?", secondSku.getId());

    assertThat(automation.processDueSchedules(10).ordersCreated()).isZero();
    assertThat(
            jdbc.queryForMap(
                "SELECT status,hold_reason FROM subscription_schedules WHERE id=?", scheduleId))
        .containsEntry("STATUS", "HELD")
        .containsEntry("HOLD_REASON", "ORDER_STOCK_UNAVAILABLE");

    subscriptions.command(
        member.getId(),
        subscriptionId,
        "remove-next-delivery-addon",
        "held-addon-remove",
        "\"1\"",
        new SubscriptionCommandRequest(null, null, null, null, secondSku.getId(), null));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_schedule_addons WHERE schedule_id=?",
                Integer.class,
                scheduleId))
        .isZero();
    assertThat(automation.processDueSchedules(10).ordersCreated()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_order_addon_items history JOIN"
                    + " subscription_orders orders ON orders.id=history.subscription_order_id WHERE"
                    + " orders.schedule_id=?",
                Integer.class,
                scheduleId))
        .isZero();
  }

  @Test
  void futurePausedAndCanceledSchedulesDoNotCreateOrders() {
    long future = createSubscription("future", basePlanVersionId, 2);
    long paused = createSubscription("paused", basePlanVersionId, 2);
    subscriptions.command(member.getId(), paused, "pause", "pause-before-due", "\"0\"", SubscriptionCommandRequest.empty());
    jdbc.update(
        "UPDATE subscription_schedules SET scheduled_date=? WHERE subscription_id=? AND"
            + " status='HELD'",
        TODAY,
        paused);
    long canceled = createSubscription("canceled", basePlanVersionId, 2);
    subscriptions.command(
        member.getId(), canceled, "cancel", "cancel-before-due", "\"0\"", SubscriptionCommandRequest.empty());
    jdbc.update(
        "UPDATE subscription_schedules SET scheduled_date=? WHERE subscription_id=? AND"
            + " status='CANCELED'",
        TODAY,
        canceled);

    SubscriptionAutomationBatchResult result = automation.processDueSchedules(10);

    assertThat(result).isEqualTo(new SubscriptionAutomationBatchResult(0, 0, 0, 0));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_orders WHERE subscription_id IN (?,?,?)",
                Integer.class,
                future,
                paused,
                canceled))
        .isZero();
  }

  @Test
  void pendingFailureRollsBackOneTargetLetsAnotherCommitAndNextTickRetries(CapturedOutput output) {
    long failedSubscriptionId = createSubscription("failed", basePlanVersionId, 2);
    subscriptions.command(
        member.getId(),
        failedSubscriptionId,
        "change-plan",
        "pending-before-failure",
        "\"0\"",
        new SubscriptionCommandRequest(null, alternatePlanVersionId, null, null, null, null));
    long failedScheduleId = moveOnlyUnprocessedSchedule(failedSubscriptionId, TODAY);
    long originalSnapshotId = currentSnapshotId(failedSubscriptionId);
    long pendingSnapshotId =
        jdbc.queryForObject(
            "SELECT snapshot_id FROM pending_plan_changes WHERE subscription_id=?",
            Long.class,
            failedSubscriptionId);
    long successfulSubscriptionId = createSubscription("successful", basePlanVersionId, 2);
    moveOnlyUnprocessedSchedule(successfulSubscriptionId, TODAY);

    doAnswer(
            invocation -> {
              int updated = (int) invocation.callRealMethod();
              if (invocation.getArgument(2, Number.class).longValue() == failedScheduleId) {
                throw new IllegalStateException("intentional transaction failure");
              }
              return updated;
            })
        .when(nativeJdbc)
        .update(
            eq(SubscriptionOrderAutomationService.UPDATE_SCHEDULE_EFFECTIVE_SQL),
            any(Object[].class));

    SubscriptionAutomationBatchResult first = automation.processDueSchedules(10);

    assertThat(first).isEqualTo(new SubscriptionAutomationBatchResult(2, 1, 1, 0));
    assertThat(orderCount(failedSubscriptionId)).isZero();
    assertThat(orderCount(successfulSubscriptionId)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT effective_snapshot_id FROM subscription_schedules WHERE id=?",
                Long.class,
                failedScheduleId))
        .isNull();
    assertThat(currentSnapshotId(failedSubscriptionId)).isEqualTo(originalSnapshotId);
    assertThat(
            jdbc.queryForObject(
                "SELECT snapshot_id FROM pending_plan_changes WHERE subscription_id=?",
                Long.class,
                failedSubscriptionId))
        .isEqualTo(pendingSnapshotId);
    assertThat(futureScheduledDates(failedSubscriptionId)).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM subscriptions WHERE id=?", Long.class, failedSubscriptionId))
        .isEqualTo(1L);
    assertThat(output)
        .contains(
            "subscriptionId=" + failedSubscriptionId,
            "scheduleId=" + failedScheduleId,
            "failureCategory=INVARIANT");

    reset(nativeJdbc);
    SubscriptionAutomationBatchResult retried = automation.processDueSchedules(10);

    assertThat(retried).isEqualTo(new SubscriptionAutomationBatchResult(1, 1, 0, 0));
    assertThat(orderCount(failedSubscriptionId)).isEqualTo(1);
    assertThat(currentSnapshotId(failedSubscriptionId)).isEqualTo(pendingSnapshotId);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM pending_plan_changes WHERE subscription_id=?",
                Integer.class,
                failedSubscriptionId))
        .isZero();
    Map<String, Object> order =
        jdbc.queryForMap(
            "SELECT effective_snapshot_id,source_plan_version_id,package_total_krw "
                + "FROM subscription_orders WHERE subscription_id=?",
            failedSubscriptionId);
    assertThat(order)
        .containsEntry("EFFECTIVE_SNAPSHOT_ID", pendingSnapshotId)
        .containsEntry("SOURCE_PLAN_VERSION_ID", alternatePlanVersionId)
        .containsEntry("PACKAGE_TOTAL_KRW", new BigDecimal("33000.00"));
    long orderId =
        jdbc.queryForObject(
            "SELECT id FROM subscription_orders WHERE subscription_id=?",
            Long.class,
            failedSubscriptionId);
    assertThat(
            jdbc.queryForMap(
                "SELECT sku_id,quantity FROM subscription_order_items WHERE order_id=?", orderId))
        .containsEntry("SKU_ID", secondSku.getId())
        .containsEntry("QUANTITY", 3);
    assertThat(futureScheduledDates(failedSubscriptionId))
        .containsExactly(LocalDate.of(2026, 8, 15));
    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM subscriptions WHERE id=?", Long.class, failedSubscriptionId))
        .isEqualTo(2L);
  }

  @Test
  void pendingCyclePromotesSubscriptionAndSchedulesFromAppliedSnapshotCycle() {
    long subscriptionId = createSubscription("cycle-promotion", basePlanVersionId, 2);
    subscriptions.command(
        member.getId(),
        subscriptionId,
        "change-delivery-cycle",
        "cycle-promotion",
        "\"0\"",
        new SubscriptionCommandRequest(null, null, 4, null, null, null));
    assertThat(
            jdbc.queryForObject(
                "SELECT delivery_cycle_weeks FROM subscriptions WHERE id=?",
                Integer.class,
                subscriptionId))
        .isEqualTo(2);
    long pendingSnapshotId =
        jdbc.queryForObject(
            "SELECT snapshot_id FROM pending_plan_changes WHERE subscription_id=?",
            Long.class,
            subscriptionId);
    moveOnlyUnprocessedSchedule(subscriptionId, TODAY);

    SubscriptionAutomationBatchResult result = automation.processDueSchedules(10);

    assertThat(result.ordersCreated()).isEqualTo(1);
    assertThat(
            jdbc.queryForMap(
                "SELECT current_snapshot_id,delivery_cycle_weeks FROM subscriptions WHERE id=?",
                subscriptionId))
        .containsEntry("CURRENT_SNAPSHOT_ID", pendingSnapshotId)
        .containsEntry("DELIVERY_CYCLE_WEEKS", 4);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM pending_plan_changes WHERE subscription_id=?",
                Integer.class,
                subscriptionId))
        .isZero();
    assertThat(futureScheduledDates(subscriptionId)).containsExactly(LocalDate.of(2026, 8, 29));
  }

  @Test
  void longDowntimeCreatesNoBacklogOrdersAndJumpsFromOriginalDate() {
    long subscriptionId = createSubscription("long-downtime", basePlanVersionId, 2);
    moveOnlyUnprocessedSchedule(subscriptionId, LocalDate.of(2026, 7, 1));

    SubscriptionAutomationBatchResult result = automation.processDueSchedules(10);

    assertThat(result.ordersCreated()).isEqualTo(1);
    assertThat(
            jdbc.queryForList(
                "SELECT scheduled_date FROM subscription_orders WHERE subscription_id=?",
                LocalDate.class,
                subscriptionId))
        .containsExactly(LocalDate.of(2026, 7, 1));
    assertThat(futureScheduledDates(subscriptionId)).containsExactly(LocalDate.of(2026, 8, 12));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? "
                    + "AND scheduled_date IN ('2026-07-15','2026-07-29')",
                Integer.class,
                subscriptionId))
        .isZero();
  }

  @Test
  void oneInvocationProcessesOnlyOldestDueSchedulePerSubscriptionAndHonorsBatchBound() {
    long firstSubscription = createSubscription("multiple-due", basePlanVersionId, 2);
    long oldestScheduleId =
        moveOnlyUnprocessedSchedule(firstSubscription, LocalDate.of(2026, 7, 1));
    jdbc.update(
        "INSERT INTO subscription_schedules("
            + "subscription_id,scheduled_date,status,effective_snapshot_id"
            + ") VALUES (?,'2026-07-15','SCHEDULED',NULL)",
        firstSubscription);
    long secondSubscription = createSubscription("batch-second", basePlanVersionId, 2);
    moveOnlyUnprocessedSchedule(secondSubscription, TODAY);
    long thirdSubscription = createSubscription("batch-third", basePlanVersionId, 2);
    moveOnlyUnprocessedSchedule(thirdSubscription, TODAY);

    SubscriptionAutomationBatchResult result = automation.processDueSchedules(2);

    assertThat(result.processedCandidates()).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_orders WHERE subscription_id=?",
                Integer.class,
                firstSubscription))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT schedule_id FROM subscription_orders WHERE subscription_id=?",
                Long.class,
                firstSubscription))
        .isEqualTo(oldestScheduleId);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_orders WHERE subscription_id IN (?,?)",
                Integer.class,
                secondSubscription,
                thirdSubscription))
        .isEqualTo(1);
  }

  @Test
  void twoSchedulerInvocationsConvergeToOneDatabaseOrder() throws Exception {
    long subscriptionId = createSubscription("concurrent", basePlanVersionId, 2);
    moveOnlyUnprocessedSchedule(subscriptionId, TODAY);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Future<SubscriptionAutomationBatchResult> first =
          executor.submit(
              () -> {
                ready.countDown();
                start.await();
                return automation.processDueSchedules(1);
              });
      Future<SubscriptionAutomationBatchResult> second =
          executor.submit(
              () -> {
                ready.countDown();
                start.await();
                return automation.processDueSchedules(1);
              });
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      List<SubscriptionAutomationBatchResult> results =
          List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));

      assertThat(
              results.stream()
                  .mapToInt(SubscriptionAutomationBatchResult::ordersCreated)
                  .sum())
          .isEqualTo(1);
      assertThat(
              results.stream()
                  .mapToInt(SubscriptionAutomationBatchResult::failures)
                  .sum())
          .isZero();
      assertThat(orderCount(subscriptionId)).isEqualTo(1);
      assertThat(futureScheduledDates(subscriptionId)).containsExactly(LocalDate.of(2026, 8, 15));
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void rescheduleAndSchedulerRaceCannotBothChangeDateAndCreateOrder() throws Exception {
    long subscriptionId = createSubscription("reschedule-race", basePlanVersionId, 2);
    long scheduleId = moveOnlyUnprocessedSchedule(subscriptionId, TODAY);
    LocalDate requestedDate = LocalDate.of(2026, 8, 20);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Future<String> command =
          executor.submit(
              () -> {
                ready.countDown();
                start.await();
                try {
                  subscriptions.command(
                      member.getId(),
                      subscriptionId,
                      "reschedule-next",
                      "reschedule-race",
                      "\"0\"",
                      new SubscriptionCommandRequest(null, null, null, requestedDate, null, null));
                  return "SUCCESS";
                } catch (SubscriptionApiException exception) {
                  return exception.code();
                }
              });
      Future<SubscriptionAutomationBatchResult> scheduler =
          executor.submit(
              () -> {
                ready.countDown();
                start.await();
                return automation.processDueSchedules(10);
              });
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      String commandOutcome = command.get(15, TimeUnit.SECONDS);
      SubscriptionAutomationBatchResult batch = scheduler.get(15, TimeUnit.SECONDS);

      assertThat(commandOutcome).isIn("SUCCESS", "SUBSCRIPTION_VERSION_MISMATCH");
      assertThat(batch.failures()).isZero();
      if ("SUCCESS".equals(commandOutcome)) {
        assertThat(orderCount(subscriptionId)).isZero();
        assertThat(
                jdbc.queryForObject(
                    "SELECT scheduled_date FROM subscription_schedules WHERE id=?",
                    LocalDate.class,
                    scheduleId))
            .isEqualTo(requestedDate);
      } else {
        assertThat(orderCount(subscriptionId)).isEqualTo(1);
        assertThat(futureScheduledDates(subscriptionId)).containsExactly(LocalDate.of(2026, 8, 15));
      }
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void activeCommandsAndAutomationPreserveOrderAndScheduleInvariants() throws Exception {
    for (String command : List.of("change-plan", "skip-next", "pause", "cancel")) {
      long subscriptionId = createSubscription("race-" + command, basePlanVersionId, 2);
      moveOnlyUnprocessedSchedule(subscriptionId, TODAY);
      SubscriptionCommandRequest body =
          "change-plan".equals(command)
              ? new SubscriptionCommandRequest(null, alternatePlanVersionId, null, null, null, null)
              : SubscriptionCommandRequest.empty();
      ExecutorService executor = Executors.newFixedThreadPool(2);
      CountDownLatch ready = new CountDownLatch(2);
      CountDownLatch start = new CountDownLatch(1);
      try {
        Future<String> commandResult =
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  try {
                    subscriptions.command(
                        member.getId(),
                        subscriptionId,
                        command,
                        "race-key-" + command,
                        "\"0\"",
                        body);
                    return "SUCCESS";
                  } catch (SubscriptionApiException exception) {
                    return exception.code();
                  }
                });
        Future<SubscriptionAutomationBatchResult> automationResult =
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  return automation.processDueSchedules(10);
                });
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        String outcome = commandResult.get(15, TimeUnit.SECONDS);
        SubscriptionAutomationBatchResult batch =
            automationResult.get(15, TimeUnit.SECONDS);

        assertThat(outcome).isIn("SUCCESS", "SUBSCRIPTION_VERSION_MISMATCH");
        assertThat(batch.failures()).isZero();
        assertThat(orderCount(subscriptionId)).isBetween(0, 1);
        assertThat(
                jdbc.queryForObject(
                    "SELECT COUNT(*) FROM (SELECT schedule_id FROM subscription_orders WHERE"
                        + " subscription_id=? GROUP BY schedule_id HAVING COUNT(*)>1) duplicates",
                    Integer.class,
                    subscriptionId))
            .isZero();
        if (orderCount(subscriptionId) == 1) {
          assertThat(futureScheduledDates(subscriptionId)).hasSize(1);
        }
      } finally {
        executor.shutdownNow();
      }
    }
  }

  @Test
  void pausedResumeRaceDoesNotTreatHeldScheduleAsDue() throws Exception {
    long subscriptionId = createSubscription("resume-race", basePlanVersionId, 2);
    subscriptions.command(
        member.getId(), subscriptionId, "pause", "pause-for-resume", "\"0\"", SubscriptionCommandRequest.empty());
    jdbc.update(
        "UPDATE subscription_schedules SET scheduled_date=? WHERE subscription_id=? AND"
            + " status='HELD'",
        TODAY,
        subscriptionId);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Future<SubscriptionResult> resumed =
          executor.submit(
              () -> {
                start.await();
                return subscriptions.command(
                    member.getId(), subscriptionId, "resume", "resume-race", "\"1\"", SubscriptionCommandRequest.empty());
              });
      Future<SubscriptionAutomationBatchResult> batch =
          executor.submit(
              () -> {
                start.await();
                return automation.processDueSchedules(10);
              });
      start.countDown();

      assertThat(resumed.get(15, TimeUnit.SECONDS).body().status()).isEqualTo("ACTIVE");
      assertThat(batch.get(15, TimeUnit.SECONDS).failures()).isZero();
      assertThat(orderCount(subscriptionId)).isZero();
      assertThat(futureScheduledDates(subscriptionId)).containsExactly(LocalDate.of(2026, 8, 15));
    } finally {
      executor.shutdownNow();
    }
  }

  private long createPlanVersion(String suffix, long price, long skuId, int quantity) {
    jdbc.update(
        "INSERT INTO subscription_plans(name,target_pet_type,on_sale) VALUES (?,?,true)",
        PLAN_PREFIX + suffix,
        "DOG");
    long planId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update(
        "INSERT INTO plan_versions(plan_id,package_price_krw,is_migration_only) VALUES (?,?,false)",
        planId,
        price);
    long versionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update(
        "INSERT INTO plan_items(plan_version_id,sku_id,quantity) VALUES (?,?,?)",
        versionId,
        skuId,
        quantity);
    jdbc.update(
        "INSERT INTO plan_version_delivery_cycles(plan_version_id,delivery_cycle_weeks) "
            + "VALUES (?,2),(?,4)",
        versionId,
        versionId);
    jdbc.update(
        "UPDATE subscription_plans SET current_plan_version_id=? WHERE id=?", versionId, planId);
    return versionId;
  }

  private long createSubscription(String key, long planVersionId, int cycle) {
    long petId =
        subscriptions
                    .createPet(member.getId(), new CreatePetRequest("반려동물-" + key, "DOG")).petId();
    SubscriptionResult result =
        subscriptions.createSubscription(
            member.getId(),
            "create-" + key,
            new CreateSubscriptionRequest(petId, planVersionId, cycle));
    return result.body().subscriptionId();
  }

  private long moveOnlyUnprocessedSchedule(long subscriptionId, LocalDate scheduledDate) {
    long scheduleId =
        jdbc.queryForObject(
            "SELECT schedule.id FROM subscription_schedules schedule LEFT JOIN subscription_orders"
                + " existing_order ON existing_order.schedule_id=schedule.id WHERE"
                + " schedule.subscription_id=? AND schedule.status='SCHEDULED' AND"
                + " existing_order.id IS NULL ORDER BY schedule.scheduled_date,schedule.id LIMIT 1",
            Long.class,
            subscriptionId);
    jdbc.update(
        "UPDATE subscription_schedules SET scheduled_date=? WHERE id=?", scheduledDate, scheduleId);
    return scheduleId;
  }

  private long currentSnapshotId(long subscriptionId) {
    return jdbc.queryForObject(
        "SELECT current_snapshot_id FROM subscriptions WHERE id=?", Long.class, subscriptionId);
  }

  private int orderCount(long subscriptionId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM subscription_orders WHERE subscription_id=?",
        Integer.class,
        subscriptionId);
  }

  private List<LocalDate> futureScheduledDates(long subscriptionId) {
    return jdbc.queryForList(
        "SELECT scheduled_date FROM subscription_schedules "
            + "WHERE subscription_id=? AND status='SCHEDULED' AND scheduled_date>? "
            + "ORDER BY scheduled_date,id",
        LocalDate.class,
        subscriptionId,
        TODAY);
  }

  private double counter(String name) {
    return meterRegistry.get(name).counter().count();
  }

  private void cleanFixtures() {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> cleanFixturesInTransaction());
  }

  private void cleanFixturesInTransaction() {
    String memberFilter =
        "SELECT id FROM members WHERE email LIKE '" + EMAIL_PREFIX + "%@example.test'";
    jdbc.update(
        "DELETE movement FROM inventory_movements movement JOIN payments payment ON"
            + " payment.id=movement.payment_id JOIN orders common_order ON"
            + " common_order.id=payment.order_id WHERE common_order.member_id IN ("
            + memberFilter
            + ")");
    jdbc.update(
        "DELETE payment FROM payments payment JOIN orders common_order ON"
            + " common_order.id=payment.order_id WHERE common_order.member_id IN ("
            + memberFilter
            + ")");
    jdbc.update(
        "DELETE context FROM subscription_order_context context JOIN orders common_order ON"
            + " common_order.id=context.order_id WHERE common_order.member_id IN ("
            + memberFilter
            + ")");
    jdbc.update(
        "DELETE item FROM order_items item JOIN orders common_order ON"
            + " common_order.id=item.order_id WHERE common_order.member_id IN ("
            + memberFilter
            + ")");
    jdbc.update("DELETE FROM orders WHERE member_id IN (" + memberFilter + ")");
    jdbc.update("DELETE FROM billing_payment_methods WHERE member_id IN (" + memberFilter + ")");
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
        "DELETE item FROM subscription_order_items item JOIN subscription_orders orders ON"
            + " orders.id=item.order_id JOIN subscriptions s ON s.id=orders.subscription_id WHERE"
            + " s.member_id IN ("
            + memberFilter
            + ")");
    jdbc.update(
        "DELETE item FROM subscription_order_addon_items item JOIN subscription_orders orders ON"
            + " orders.id=item.subscription_order_id JOIN subscriptions s ON"
            + " s.id=orders.subscription_id WHERE s.member_id IN ("
            + memberFilter
            + ")");
    jdbc.update(
        "DELETE orders FROM subscription_orders orders JOIN subscriptions s ON"
            + " s.id=orders.subscription_id WHERE s.member_id IN ("
            + memberFilter
            + ")");
    jdbc.update(
        "DELETE addon FROM subscription_schedule_addons addon JOIN subscription_schedules schedule"
            + " ON schedule.id=addon.schedule_id JOIN subscriptions s ON"
            + " s.id=schedule.subscription_id WHERE s.member_id IN ("
            + memberFilter
            + ")");
    jdbc.update(
        "DELETE snapshot FROM subscription_shipping_snapshots snapshot JOIN subscriptions s ON"
            + " s.id=snapshot.subscription_id WHERE s.member_id IN ("
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
    jdbc.update("DELETE FROM categories WHERE slug LIKE ?", "sub-auto-%");
    jdbc.update(
        "UPDATE members SET default_address_id=NULL WHERE email LIKE ?",
        EMAIL_PREFIX + "%@example.test");
    jdbc.update("DELETE FROM member_addresses WHERE member_id IN (" + memberFilter + ")");
    jdbc.update("DELETE FROM members WHERE email LIKE ?", EMAIL_PREFIX + "%@example.test");
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FixedClockConfiguration {
    @Bean
    @Primary
    Clock fixedSubscriptionAutomationClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }
}
