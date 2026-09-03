package com.pawcycle.backend.subscription.performance;

import com.pawcycle.backend.subscription.SubscriptionAutomationBatchResult;
import com.pawcycle.backend.subscription.SubscriptionOrderAutomationService;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("subscription-burst-measurement & !production & !prod")
public class SubscriptionBurstMeasurementService {

  static final String FIXTURE_EMAIL_PREFIX = "perf-ph10-002-";
  static final String FIXTURE_EMAIL_SUFFIX = "@synthetic.invalid";
  static final String FIXTURE_PRODUCT_NAME = "PERF-PH10-002 synthetic product";
  static final int DEFAULT_BATCH_SIZE = 100;
  static final long DEFAULT_FIXED_DELAY_MS = 60_000L;
  private static final int MAX_COHORT_SIZE = 10_000;
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final NativeQueryExecutor jdbc;
  private final SubscriptionOrderAutomationService automation;
  private final Clock clock;
  private final Path workloadStartMarker;
  private final boolean runArmed;
  private final String markerWorkloadIdentity;
  private final String markerSourceSha;
  private final int markerCohort;
  private final int measurementBatchSize;
  private final long measurementFixedDelayMs;
  private final AtomicBoolean drainRunning = new AtomicBoolean();

  public SubscriptionBurstMeasurementService(
      NativeQueryExecutor jdbc,
      SubscriptionOrderAutomationService automation,
      Clock clock,
      @Value("${pawcycle.subscription-burst-measurement.workload-start-marker-path}")
          String workloadStartMarkerPath,
      @Value("${pawcycle.subscription-burst-measurement.run-armed:false}") boolean runArmed,
      @Value("${pawcycle.subscription-burst-measurement.marker-workload-identity:}")
          String markerWorkloadIdentity,
      @Value("${pawcycle.subscription-burst-measurement.marker-source-sha:}")
          String markerSourceSha,
      @Value("${pawcycle.subscription-burst-measurement.marker-cohort:0}") int markerCohort,
      @Value("${pawcycle.subscription-burst-measurement.batch-size:100}") int measurementBatchSize,
      @Value("${pawcycle.subscription-burst-measurement.fixed-delay-ms:60000}")
          long measurementFixedDelayMs) {
    this.jdbc = jdbc;
    this.automation = automation;
    this.clock = clock;
    this.workloadStartMarker = Path.of(workloadStartMarkerPath).toAbsolutePath().normalize();
    this.runArmed = runArmed;
    this.markerWorkloadIdentity = markerWorkloadIdentity;
    this.markerSourceSha = markerSourceSha;
    this.markerCohort = markerCohort;
    if (measurementBatchSize < 1 || measurementFixedDelayMs < 1) {
      throw new IllegalArgumentException(
          "Subscription Burst measurement batch-size and fixed-delay-ms must be positive");
    }
    this.measurementBatchSize = measurementBatchSize;
    this.measurementFixedDelayMs = measurementFixedDelayMs;
  }

  @Transactional
  public synchronized SubscriptionFixtureSummary setup(int cohortSize) {
    assertRunArmed();
    if (cohortSize < 1 || cohortSize > MAX_COHORT_SIZE) {
      throw new IllegalArgumentException("cohortSize must be between 1 and 10000");
    }
    if (fixtureMemberCount() != 0) {
      throw new IllegalStateException("Subscription Burst fixture already exists in this database");
    }

    LocalDate today = LocalDate.ofInstant(clock.instant(), SEOUL);
    LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    CatalogFixture catalog = createCatalogAndPlan(cohortSize);
    FixtureRows rows = insertFixtureRows(cohortSize, catalog, today, now);
    if (rows == null || rows.members() != cohortSize || rows.subscriptions() != cohortSize) {
      throw new IllegalStateException("Synthetic fixture cardinality is incomplete");
    }
    int backlog = dueBacklog();
    if (backlog != cohortSize || fixtureOrderCount() != 0) {
      throw new IllegalStateException("Synthetic fixture is not a clean due backlog");
    }
    return new SubscriptionFixtureSummary(
        cohortSize, backlog, measurementBatchSize, measurementFixedDelayMs);
  }

  public SubscriptionDrainSummary drain() {
    assertRunArmed();
    if (!drainRunning.compareAndSet(false, true)) {
      throw new IllegalStateException("Subscription Burst drain is already running");
    }
    try {
      int initialBacklog = dueBacklog();
      assertEligibleCandidateScope(initialBacklog);
      if (initialBacklog < 1) {
        throw new IllegalStateException("Synthetic due backlog is unavailable");
      }
      assertWorkloadMarkerContract(initialBacklog);
      int expectedTicks = (initialBacklog + measurementBatchSize - 1) / measurementBatchSize;
      int safetyLimit = expectedTicks + 2;
      List<SubscriptionBatchMeasurement> batches = new ArrayList<>();
      int processed = 0;
      int created = 0;
      int failures = 0;
      int duplicateOrNoOp = 0;
      long drainStart = System.nanoTime();
      writeWorkloadStartMarker();
      for (int sequence = 1; sequence <= safetyLimit; sequence++) {
        long batchStart = System.nanoTime();
        SubscriptionAutomationBatchResult result =
            automation.processDueSchedules(measurementBatchSize);
        double durationMs = elapsedMilliseconds(batchStart);
        batches.add(
            new SubscriptionBatchMeasurement(
                sequence,
                result.processedCandidates(),
                result.ordersCreated(),
                result.failures(),
                result.duplicateOrNoOp(),
                durationMs));
        processed += result.processedCandidates();
        created += result.ordersCreated();
        failures += result.failures();
        duplicateOrNoOp += result.duplicateOrNoOp();
        if (result.processedCandidates() == 0 || created >= initialBacklog) {
          break;
        }
      }
      double rawDrainElapsedMs = elapsedMilliseconds(drainStart);
      int finalBacklog = dueBacklog();
      int orderCount = fixtureOrderCount();
      int duplicateScheduleOrders = fixtureDuplicateScheduleOrderCount();
      int futureSchedules = fixtureFutureScheduleCount();
      List<Double> durations =
          batches.stream().map(SubscriptionBatchMeasurement::durationMs).sorted().toList();
      double ordersPerSecond = rawDrainElapsedMs > 0 ? created / (rawDrainElapsedMs / 1_000.0) : 0;
      long projectedCompletionMs =
          Math.round(rawDrainElapsedMs) + Math.max(0, expectedTicks - 1) * measurementFixedDelayMs;
      boolean harnessFailure =
          finalBacklog != 0
              || created != initialBacklog
              || failures != 0
              || duplicateOrNoOp != 0
              || orderCount != initialBacklog
              || duplicateScheduleOrders != 0
              || futureSchedules != initialBacklog;
      return new SubscriptionDrainSummary(
          initialBacklog,
          finalBacklog,
          batches.size(),
          processed,
          created,
          failures,
          duplicateOrNoOp,
          rawDrainElapsedMs,
          ordersPerSecond,
          percentile(durations, 0.50),
          percentile(durations, 0.95),
          durations.isEmpty() ? 0 : durations.getLast(),
          measurementBatchSize,
          measurementFixedDelayMs,
          expectedTicks,
          projectedCompletionMs,
          "projection: observed raw drain duration plus "
              + measurementFixedDelayMs
              + "ms fixed delay between configured scheduler ticks",
          orderCount,
          duplicateScheduleOrders,
          futureSchedules,
          harnessFailure,
          List.copyOf(batches));
    } finally {
      drainRunning.set(false);
    }
  }

  private void assertRunArmed() {
    if (!runArmed) {
      throw new IllegalStateException("Subscription Burst measurement endpoint is disarmed");
    }
  }

  private void assertEligibleCandidateScope(int expectedFixtureCandidates) {
    var scope =
        jdbc.queryForMap(
            """
            SELECT
              COALESCE(SUM(CASE WHEN member.email LIKE ? THEN 1 ELSE 0 END), 0) AS fixture_candidates,
              COALESCE(SUM(CASE WHEN member.email NOT LIKE ? THEN 1 ELSE 0 END), 0) AS non_fixture_candidates
            FROM subscription_schedules schedule
            JOIN subscriptions subscription ON subscription.id = schedule.subscription_id
            JOIN members member ON member.id = subscription.member_id
            LEFT JOIN subscription_orders existing_order ON existing_order.schedule_id = schedule.id
            WHERE subscription.runtime_managed = true
              AND subscription.status = 'ACTIVE'
              AND schedule.status = 'SCHEDULED'
              AND schedule.scheduled_date <= ?
              AND existing_order.id IS NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM subscription_schedules prior_schedule
                  JOIN subscription_order_context prior_context ON prior_context.schedule_id = prior_schedule.id
                  JOIN payments prior_payment ON prior_payment.order_id = prior_context.order_id
                  WHERE prior_schedule.subscription_id = schedule.subscription_id
                    AND (prior_schedule.scheduled_date < schedule.scheduled_date
                         OR (prior_schedule.scheduled_date = schedule.scheduled_date AND prior_schedule.id < schedule.id))
                    AND prior_payment.status <> 'SUCCEEDED'
              )
              AND NOT EXISTS (
                  SELECT 1
                  FROM subscription_schedules earlier
                  LEFT JOIN subscription_orders earlier_order ON earlier_order.schedule_id = earlier.id
                  WHERE earlier.subscription_id = schedule.subscription_id
                    AND earlier.status = 'SCHEDULED'
                    AND earlier.scheduled_date <= ?
                    AND earlier_order.id IS NULL
                    AND (earlier.scheduled_date < schedule.scheduled_date
                         OR (earlier.scheduled_date = schedule.scheduled_date AND earlier.id < schedule.id))
              )
            """,
            fixtureEmailLike(),
            fixtureEmailLike(),
            LocalDate.ofInstant(clock.instant(), SEOUL),
            LocalDate.ofInstant(clock.instant(), SEOUL));
    long fixtureCandidates = ((Number) scope.get("fixture_candidates")).longValue();
    long nonFixtureCandidates = ((Number) scope.get("non_fixture_candidates")).longValue();
    if (fixtureCandidates != expectedFixtureCandidates || nonFixtureCandidates != 0) {
      throw new IllegalStateException(
          "Eligible automation candidates are outside the synthetic fixture scope");
    }
  }

  void assertWorkloadMarkerContract(int initialBacklog) {
    boolean legacyContract =
        markerWorkloadIdentity.isBlank() && markerSourceSha.isBlank() && markerCohort == 0;
    if (legacyContract) return;
    if (!markerWorkloadIdentity.matches("[a-z0-9-]+")
        || !markerSourceSha.matches("[0-9a-fA-F]{40}")
        || markerCohort != initialBacklog) {
      throw new IllegalStateException(
          "Authoritative workload-start marker identity contract is invalid");
    }
  }

  void writeWorkloadStartMarker() {
    String startedAt = clock.instant().toString();
    boolean legacyContract =
        markerWorkloadIdentity.isBlank() && markerSourceSha.isBlank() && markerCohort == 0;
    String marker =
        legacyContract
            ? "{\"workloadInvocationStarted\":true,\"workloadStartedAtUtc\":\"" + startedAt + "\"}"
            : "{\"workloadIdentity\":\""
                + markerWorkloadIdentity
                + "\",\"sourceSha\":\""
                + markerSourceSha.toLowerCase(java.util.Locale.ROOT)
                + "\",\"cohort\":"
                + markerCohort
                + ",\"workloadInvocationStarted\":true,\"workloadStartedAtUtc\":\""
                + startedAt
                + "\"}";
    try {
      Files.writeString(
          workloadStartMarker,
          marker,
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE_NEW,
          StandardOpenOption.WRITE);
    } catch (java.io.IOException exception) {
      throw new IllegalStateException(
          "Authoritative workload-start marker could not be recorded", exception);
    }
  }

  private CatalogFixture createCatalogAndPlan(int cohortSize) {
    jdbc.update(
        "INSERT INTO categories(name,slug,display_order,active) VALUES ('PERF-PH10-002"
            + " synthetic','perf-ph10-002-synthetic',0,true)");
    long categoryId = lastInsertId();
    jdbc.update(
        "INSERT INTO"
            + " products(brand_id,category_id,name,short_description,description,pet_type,thumbnail_url,display_status)"
            + " VALUES (1,?,?,'synthetic measurement fixture',NULL,'DOG',NULL,'PUBLIC')",
        categoryId,
        FIXTURE_PRODUCT_NAME);
    long productId = lastInsertId();
    jdbc.update(
        "INSERT INTO skus(product_id,sku_code,name,price,subscribable,display_order,status) VALUES"
            + " (?,'PERF-PH10-002-SKU','synthetic SKU',1000.00,true,0,'ACTIVE')",
        productId);
    long skuId = lastInsertId();
    jdbc.update(
        "INSERT INTO inventories(sku_id,available_quantity,reserved_quantity,version) VALUES"
            + " (?,?,0,0)",
        skuId,
        cohortSize + 100);
    jdbc.update(
        "INSERT INTO"
            + " subscription_plans(name,target_pet_type,on_sale,sale_starts_on,sale_ends_on,current_plan_version_id)"
            + " VALUES ('PERF-PH10-002 synthetic plan','DOG',true,NULL,NULL,NULL)");
    long planId = lastInsertId();
    jdbc.update(
        "INSERT INTO plan_versions(plan_id,package_price_krw,is_migration_only) VALUES"
            + " (?,1000,false)",
        planId);
    long planVersionId = lastInsertId();
    jdbc.update(
        "INSERT INTO plan_items(plan_version_id,sku_id,quantity) VALUES (?,?,1)",
        planVersionId,
        skuId);
    jdbc.update(
        "INSERT INTO plan_version_delivery_cycles(plan_version_id,delivery_cycle_weeks) VALUES"
            + " (?,2)",
        planVersionId);
    jdbc.update(
        "UPDATE subscription_plans SET current_plan_version_id=? WHERE id=?",
        planVersionId,
        planId);
    return new CatalogFixture(skuId, planVersionId);
  }

  private FixtureRows insertFixtureRows(
      int cohortSize, CatalogFixture catalog, LocalDate today, LocalDateTime now) {
    long[] memberIds =
        insertRows(
            cohortSize,
            index ->
                insertAndGetId(
                    "INSERT INTO members(email,password_hash,role) VALUES (?,?,'USER')",
                    fixtureEmail(index),
                    "synthetic-no-login"));
    long[] addressIds =
        insertRows(
            cohortSize,
            index ->
                insertAndGetId(
                    "INSERT INTO member_addresses(member_id,name,recipient_name,recipient_phone,postal_code,address_line1,address_line2,created_at,updated_at) VALUES (?,'synthetic','synthetic recipient','000-0000-0000','00000','synthetic address',NULL,?,?)",
                    memberIds[index],
                    now,
                    now));
    batch(
        cohortSize,
        index -> jdbc.update("UPDATE members SET default_address_id=? WHERE id=?", addressIds[index], memberIds[index]));
    batch(
        cohortSize,
        index -> jdbc.update(
            "INSERT INTO billing_payment_methods(member_id,provider,customer_key,billing_key,status,created_at) VALUES (?,'TOSS',?,?,'ACTIVE',?)",
            memberIds[index],
            "perf-ph10-002-customer-" + index,
            "perf-ph10-002-billing-" + index,
            now));
    long[] petIds =
        insertRows(
            cohortSize,
            index -> insertAndGetId("INSERT INTO pets(member_id,name,pet_type) VALUES (?,'synthetic pet','DOG')", memberIds[index]));
    long[] subscriptionIds =
        insertRows(
            cohortSize,
            index ->
                insertAndGetId(
                    "INSERT INTO subscriptions(member_id,sku_id,quantity,delivery_cycle_weeks,created_date,next_order_date,pet_id,status,version,current_snapshot_id,legacy_api_visible,runtime_managed) VALUES (?,?,1,2,?,?,?,'ACTIVE',0,NULL,false,true)",
                    memberIds[index],
                    catalog.skuId(),
                    today.minusWeeks(2),
                    today,
                    petIds[index]));
    long[] snapshotIds =
        insertRows(
            cohortSize,
            index ->
                insertAndGetId(
                    "INSERT INTO subscription_snapshots(subscription_id,source_plan_version_id,package_total_krw,delivery_cycle_weeks) VALUES (?,?,1000,2)",
                    subscriptionIds[index],
                    catalog.planVersionId()));
    batch(
        cohortSize,
        index -> jdbc.update("UPDATE subscriptions SET current_snapshot_id=? WHERE id=?", snapshotIds[index], subscriptionIds[index]));
    batch(
        cohortSize,
        index -> jdbc.update("INSERT INTO subscription_snapshot_items(snapshot_id,sku_id,quantity) VALUES (?,?,1)", snapshotIds[index], catalog.skuId()));
    batch(
        cohortSize,
        index -> jdbc.update("INSERT INTO subscription_schedules(subscription_id,scheduled_date,status,effective_snapshot_id) VALUES (? ,?,'SCHEDULED',NULL)", subscriptionIds[index], today));
    batch(
        cohortSize,
        index -> jdbc.update("INSERT INTO subscription_shipping_snapshots(subscription_id,recipient_name,recipient_phone,postal_code,address_line1,address_line2,updated_at) VALUES (?,'synthetic recipient','000-0000-0000','00000','synthetic address',NULL,?)", subscriptionIds[index], now));
    return new FixtureRows(memberIds.length, subscriptionIds.length);
  }

  private long[] insertRows(int count, IntFunction<Long> inserter) {
    long[] ids = new long[count];
    for (int index = 0; index < count; index++) ids[index] = inserter.apply(index);
    return ids;
  }

  private void batch(int count, IntConsumer action) {
    for (int index = 0; index < count; index++) action.accept(index);
  }

  private long insertAndGetId(String sql, Object... arguments) {
    jdbc.update(sql, arguments);
    return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }

  private int dueBacklog() {
    return jdbc.queryForObject(
        """
        SELECT COUNT(*)
        FROM subscription_schedules schedule
        JOIN subscriptions subscription ON subscription.id=schedule.subscription_id
        JOIN members member ON member.id=subscription.member_id
        LEFT JOIN subscription_orders existing_order ON existing_order.schedule_id=schedule.id
        WHERE member.email LIKE ?
          AND subscription.runtime_managed=true
          AND subscription.status='ACTIVE'
          AND schedule.status='SCHEDULED'
          AND schedule.scheduled_date<=?
          AND existing_order.id IS NULL
        """,
        Integer.class,
        fixtureEmailLike(),
        LocalDate.ofInstant(clock.instant(), SEOUL));
  }

  private int fixtureMemberCount() {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM members WHERE email LIKE ?", Integer.class, fixtureEmailLike());
  }

  private int fixtureOrderCount() {
    return jdbc.queryForObject(
        """
        SELECT COUNT(*) FROM subscription_orders result
        JOIN subscriptions subscription ON subscription.id=result.subscription_id
        JOIN members member ON member.id=subscription.member_id
        WHERE member.email LIKE ?
        """,
        Integer.class,
        fixtureEmailLike());
  }

  private int fixtureDuplicateScheduleOrderCount() {
    return jdbc.queryForObject(
        """
        SELECT COUNT(*) FROM (
          SELECT result.schedule_id
          FROM subscription_orders result
          JOIN subscriptions subscription ON subscription.id=result.subscription_id
          JOIN members member ON member.id=subscription.member_id
          WHERE member.email LIKE ?
          GROUP BY result.schedule_id HAVING COUNT(*)>1
        ) duplicate_schedule
        """,
        Integer.class,
        fixtureEmailLike());
  }

  private int fixtureFutureScheduleCount() {
    return jdbc.queryForObject(
        """
        SELECT COUNT(*) FROM subscription_schedules schedule
        JOIN subscriptions subscription ON subscription.id=schedule.subscription_id
        JOIN members member ON member.id=subscription.member_id
        WHERE member.email LIKE ? AND schedule.status='SCHEDULED' AND schedule.scheduled_date>?
        """,
        Integer.class,
        fixtureEmailLike(),
        LocalDate.ofInstant(clock.instant(), SEOUL));
  }

  private long lastInsertId() {
    return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }

  private static String fixtureEmail(int index) {
    return FIXTURE_EMAIL_PREFIX + index + FIXTURE_EMAIL_SUFFIX;
  }

  private static String fixtureEmailLike() {
    return FIXTURE_EMAIL_PREFIX + "%" + FIXTURE_EMAIL_SUFFIX;
  }

  private static double elapsedMilliseconds(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000.0;
  }

  private static double percentile(List<Double> sortedValues, double quantile) {
    if (sortedValues.isEmpty()) return 0;
    int index = Math.max(0, (int) Math.ceil(sortedValues.size() * quantile) - 1);
    return sortedValues.get(index);
  }

  private record FixtureRows(int members, int subscriptions) {}

  private record CatalogFixture(long skuId, long planVersionId) {}

}
