package com.pawcycle.backend.subscription;

import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SubscriptionOrderAutomationService {

  static final String UPDATE_SCHEDULE_EFFECTIVE_SQL =
      "UPDATE subscription_schedules SET"
          + " effective_snapshot_id=?,status='SCHEDULED',hold_reason=NULL WHERE id=?";

  private static final Logger log =
      LoggerFactory.getLogger(SubscriptionOrderAutomationService.class);
  private static final String AUTOMATION_FAILURE_LOG =
      "Subscription order automation failed; subscriptionId={}, scheduleId={}, failureCategory={}";
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final String FIND_DUE_CANDIDATES_SQL =
      """
      SELECT schedule.id AS schedule_id, schedule.subscription_id
      FROM subscription_schedules schedule
      JOIN subscriptions subscription ON subscription.id = schedule.subscription_id
      LEFT JOIN subscription_orders existing_order ON existing_order.schedule_id = schedule.id
      WHERE subscription.runtime_managed = true
        AND subscription.status = 'ACTIVE'
        AND (schedule.status = 'SCHEDULED'
             OR (schedule.status = 'HELD' AND schedule.hold_reason = 'ORDER_STOCK_UNAVAILABLE'))
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
              AND (earlier.status = 'SCHEDULED'
                   OR (earlier.status = 'HELD' AND earlier.hold_reason = 'ORDER_STOCK_UNAVAILABLE'))
              AND earlier.scheduled_date <= ?
              AND earlier_order.id IS NULL
              AND (earlier.scheduled_date < schedule.scheduled_date
                   OR (earlier.scheduled_date = schedule.scheduled_date AND earlier.id < schedule.id))
        )
      ORDER BY schedule.scheduled_date, schedule.id
      LIMIT ?
      """;

  private final NativeQueryExecutor jdbc;
  private final Clock clock;
  private final SubscriptionOrderAutomationMetrics metrics;
  private final TransactionTemplate targetTransaction;

  public SubscriptionOrderAutomationService(
      NativeQueryExecutor jdbc,
      Clock clock,
      SubscriptionOrderAutomationMetrics metrics,
      PlatformTransactionManager transactionManager) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.metrics = metrics;
    this.targetTransaction = new TransactionTemplate(transactionManager);
    this.targetTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public SubscriptionAutomationBatchResult processDueSchedules(int batchSize) {
    if (batchSize < 1) {
      throw new IllegalArgumentException("batchSize must be positive");
    }

    Timer.Sample sample = metrics.start();
    int processed = 0;
    int created = 0;
    int failures = 0;
    int duplicateOrNoOp = 0;
    try {
      LocalDate today = today();
      List<Candidate> candidates =
          jdbc.query(
              FIND_DUE_CANDIDATES_SQL,
              (rs, rowNumber) ->
                  new Candidate(rs.getLong("subscription_id"), rs.getLong("schedule_id")),
              today,
              today,
              batchSize);
      for (Candidate candidate : candidates) {
        processed++;
        try {
          ProcessingOutcome outcome =
              targetTransaction.execute(status -> processCandidate(candidate, today));
          if (outcome == ProcessingOutcome.CREATED) {
            created++;
          } else {
            duplicateOrNoOp++;
          }
        } catch (RuntimeException exception) {
          failures++;
          log.error(
              AUTOMATION_FAILURE_LOG,
              candidate.subscriptionId(),
              candidate.scheduleId(),
              failureCategory(exception));
        }
      }
      return new SubscriptionAutomationBatchResult(
          processed, created, failures, duplicateOrNoOp);
    } catch (RuntimeException exception) {
      failures++;
      log.error(
          "Subscription order automation batch failed; failureCategory={}",
          failureCategory(exception));
      throw exception;
    } finally {
      metrics.finish(sample, processed, created, failures, duplicateOrNoOp);
    }
  }

  private ProcessingOutcome processCandidate(Candidate candidate, LocalDate today) {
    Optional<Map<String, Object>> maybeSubscription =
        one(
            "SELECT"
                + " id,member_id,status,runtime_managed,version,current_snapshot_id,delivery_cycle_weeks"
                + " FROM subscriptions WHERE id=? FOR UPDATE",
            candidate.subscriptionId());
    if (maybeSubscription.isEmpty()) {
      return ProcessingOutcome.NO_OP;
    }
    Map<String, Object> subscription = maybeSubscription.get();
    if (!Boolean.TRUE.equals(subscription.get("runtime_managed"))
        || !"ACTIVE".equals(subscription.get("status"))) {
      return ProcessingOutcome.NO_OP;
    }

    Optional<Map<String, Object>> maybeSchedule =
        one(
            "SELECT id,subscription_id,scheduled_date,status,hold_reason,effective_snapshot_id "
                + "FROM subscription_schedules WHERE id=? FOR UPDATE",
            candidate.scheduleId());
    if (maybeSchedule.isEmpty()) {
      return ProcessingOutcome.NO_OP;
    }
    Map<String, Object> schedule = maybeSchedule.get();
    LocalDate scheduledDate = dateValue(schedule, "scheduled_date");
    if (longValue(schedule, "subscription_id") != candidate.subscriptionId()
        || !isDueStatus(schedule)
        || scheduledDate.isAfter(today)) {
      return ProcessingOutcome.NO_OP;
    }
    if (orderExists(candidate.scheduleId())) {
      return ProcessingOutcome.NO_OP;
    }
    Map<String, Object> shipping =
        shippingSnapshotOrDefault(candidate.subscriptionId(), longValue(subscription, "member_id"));
    if (shipping == null) {
      hold(candidate.scheduleId(), "MISSING_SHIPPING_ADDRESS");
      return ProcessingOutcome.NO_OP;
    }
    if (one(
            "SELECT id FROM billing_payment_methods WHERE member_id=? AND status='ACTIVE' FOR"
                + " UPDATE",
            longValue(subscription, "member_id"))
        .isEmpty()) {
      hold(candidate.scheduleId(), "MISSING_BILLING_METHOD");
      return ProcessingOutcome.NO_OP;
    }

    long currentSnapshotId = longValue(subscription, "current_snapshot_id");
    Map<String, Object> currentSnapshot = snapshot(candidate.subscriptionId(), currentSnapshotId);
    Optional<Map<String, Object>> pending =
        one(
            "SELECT snapshot_id,target_schedule_id FROM pending_plan_changes "
                + "WHERE subscription_id=? FOR UPDATE",
            candidate.subscriptionId());
    boolean appliesPending =
        pending
            .map(row -> longValue(row, "target_schedule_id") == candidate.scheduleId())
            .orElse(false);
    long effectiveSnapshotId;
    if (appliesPending) {
      effectiveSnapshotId = longValue(pending.orElseThrow(), "snapshot_id");
    } else {
      effectiveSnapshotId = currentSnapshotId;
    }
    Map<String, Object> effectiveSnapshot =
        snapshot(candidate.subscriptionId(), effectiveSnapshotId);
    int currentDeliveryCycleWeeks = intValue(currentSnapshot, "delivery_cycle_weeks");
    if (intValue(subscription, "delivery_cycle_weeks") != currentDeliveryCycleWeeks) {
      throw new IllegalStateException("Subscription delivery cycle differs from current snapshot");
    }
    int effectiveDeliveryCycleWeeks = intValue(effectiveSnapshot, "delivery_cycle_weeks");

    List<Map<String, Object>> items =
        jdbc.queryForList(
            "SELECT sku_id,quantity FROM subscription_snapshot_items "
                + "WHERE snapshot_id=? ORDER BY sku_id",
            effectiveSnapshotId);
    if (items.isEmpty()) {
      throw new IllegalStateException("Effective snapshot has no order items");
    }
    List<Map<String, Object>> addOns =
        jdbc.queryForList(
            """
            SELECT addon.sku_id,addon.quantity,addon.unit_price_krw,sku.sku_code,sku.name AS sku_name,
                   sku.status AS sku_status,product.name AS product_name,product.display_status,
                   category.active AS category_active,brand.active AS brand_active
            FROM subscription_schedule_addons addon JOIN skus sku ON sku.id=addon.sku_id
            JOIN products product ON product.id=sku.product_id JOIN categories category ON category.id=product.category_id
            JOIN brands brand ON brand.id=product.brand_id
            WHERE addon.schedule_id=? ORDER BY addon.sku_id FOR UPDATE\
            """,
            candidate.scheduleId());
    if (hasUnavailableStock(items, addOns)) {
      hold(candidate.scheduleId(), "ORDER_STOCK_UNAVAILABLE");
      return ProcessingOutcome.NO_OP;
    }
    if (addOns.stream()
        .anyMatch(
            addon ->
                !"ACTIVE".equals(addon.get("sku_status"))
                    || !"PUBLIC".equals(addon.get("display_status"))
                    || !Boolean.TRUE.equals(addon.get("category_active"))
                    || !Boolean.TRUE.equals(addon.get("brand_active")))) {
      hold(candidate.scheduleId(), "ORDER_STOCK_UNAVAILABLE");
      return ProcessingOutcome.NO_OP;
    }

    long orderId =
        createCommonOrder(subscription, schedule, effectiveSnapshot, shipping, items, addOns);
    jdbc.update(
        "INSERT INTO"
            + " subscription_orders(member_id,subscription_id,schedule_id,effective_snapshot_id,source_plan_version_id,scheduled_date,processed_at,package_total_krw,status)"
            + " VALUES (?,?,?,?,?,?,?,?,'CREATED')",
        longValue(subscription, "member_id"),
        candidate.subscriptionId(),
        candidate.scheduleId(),
        effectiveSnapshotId,
        longValue(effectiveSnapshot, "source_plan_version_id"),
        scheduledDate,
        LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
        decimalValue(effectiveSnapshot, "package_total_krw").add(addOnTotal(addOns)));
    long subscriptionOrderId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    for (Map<String, Object> item : items) {
      jdbc.update(
          "INSERT INTO subscription_order_items(order_id,sku_id,quantity) VALUES (?,?,?)",
          subscriptionOrderId,
          longValue(item, "sku_id"),
          intValue(item, "quantity"));
    }
    for (Map<String, Object> addon : addOns) {
      jdbc.update(
          "INSERT INTO"
              + " subscription_order_addon_items(subscription_order_id,sku_id,quantity,unit_price_krw)"
              + " VALUES (?,?,?,?)",
          subscriptionOrderId,
          longValue(addon, "sku_id"),
          intValue(addon, "quantity"),
          decimalValue(addon, "unit_price_krw"));
    }
    jdbc.update(
        "DELETE FROM subscription_schedule_addons WHERE schedule_id=?", candidate.scheduleId());

    jdbc.update(UPDATE_SCHEDULE_EFFECTIVE_SQL, effectiveSnapshotId, candidate.scheduleId());
    jdbc.update(
        "DELETE FROM notifications WHERE type='SUBSCRIPTION_DELIVERY_REMINDER' AND"
            + " reference_type='SCHEDULE' AND reference_id=?",
        candidate.scheduleId());
    if (appliesPending) {
      int promoted =
          jdbc.update(
              "UPDATE subscriptions SET current_snapshot_id=?,delivery_cycle_weeks=? "
                  + "WHERE id=? AND current_snapshot_id=? AND delivery_cycle_weeks=?",
              effectiveSnapshotId,
              effectiveDeliveryCycleWeeks,
              candidate.subscriptionId(),
              currentSnapshotId,
              currentDeliveryCycleWeeks);
      int removed =
          jdbc.update(
              "DELETE FROM pending_plan_changes "
                  + "WHERE subscription_id=? AND snapshot_id=? AND target_schedule_id=?",
              candidate.subscriptionId(),
              effectiveSnapshotId,
              candidate.scheduleId());
      if (promoted != 1 || removed != 1) {
        throw new IllegalStateException("Pending snapshot promotion lost its target");
      }
    }

    LocalDate nextScheduledDate =
        firstFutureDate(scheduledDate, effectiveDeliveryCycleWeeks, today);
    List<Map<String, Object>> futureSchedules =
        jdbc.queryForList(
            "SELECT id,scheduled_date FROM subscription_schedules "
                + "WHERE subscription_id=? AND status='SCHEDULED' AND scheduled_date>? "
                + "ORDER BY scheduled_date,id FOR UPDATE",
            candidate.subscriptionId(),
            today);
    if (futureSchedules.isEmpty()) {
      jdbc.update(
          "INSERT INTO subscription_schedules("
              + "subscription_id,scheduled_date,status,effective_snapshot_id"
              + ") VALUES (?,?,'SCHEDULED',NULL)",
          candidate.subscriptionId(),
          nextScheduledDate);
    } else if (futureSchedules.size() != 1
        || !nextScheduledDate.equals(dateValue(futureSchedules.getFirst(), "scheduled_date"))) {
      throw new IllegalStateException("Future Schedule cardinality is not safely recoverable");
    }

    long expectedVersion = longValue(subscription, "version");
    int versionUpdated =
        jdbc.update(
            "UPDATE subscriptions SET version=version+1 WHERE id=? AND version=?",
            candidate.subscriptionId(),
            expectedVersion);
    if (versionUpdated != 1) {
      throw new IllegalStateException("Subscription version changed while locked");
    }
    return ProcessingOutcome.CREATED;
  }

  private Map<String, Object> shippingSnapshotOrDefault(long subscriptionId, long memberId) {
    Optional<Map<String, Object>> existing =
        one(
            "SELECT recipient_name,recipient_phone,postal_code,address_line1,address_line2 "
                + "FROM subscription_shipping_snapshots WHERE subscription_id=? FOR UPDATE",
            subscriptionId);
    if (existing.isPresent()) return existing.get();
    Optional<Map<String, Object>> defaultAddress =
        one(
            """
            SELECT address.recipient_name,address.recipient_phone,address.postal_code,address.address_line1,address.address_line2
            FROM members member JOIN member_addresses address ON address.id=member.default_address_id
            WHERE member.id=? FOR UPDATE\
            """,
            memberId);
    if (defaultAddress.isEmpty()) return null;
    Map<String, Object> address = defaultAddress.get();
    jdbc.update(
        """
        INSERT INTO subscription_shipping_snapshots(subscription_id,recipient_name,recipient_phone,postal_code,address_line1,address_line2,updated_at)
        VALUES (?,?,?,?,?,?,?)\
        """,
        subscriptionId,
        address.get("recipient_name"),
        address.get("recipient_phone"),
        address.get("postal_code"),
        address.get("address_line1"),
        address.get("address_line2"),
        LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    return address;
  }

  private long createCommonOrder(
      Map<String, Object> subscription,
      Map<String, Object> schedule,
      Map<String, Object> effectiveSnapshot,
      Map<String, Object> shipping,
      List<Map<String, Object>> snapshotItems,
      List<Map<String, Object>> addOns) {
    BigDecimal total = decimalValue(effectiveSnapshot, "package_total_krw").add(addOnTotal(addOns));
    String orderNumber = "SUB-" + UUID.randomUUID();
    LocalDateTime timestamp = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    jdbc.update(
        """
        INSERT INTO orders(order_number,member_id,source,status,original_amount,discount_amount,shipping_fee,payment_amount,
         recipient_name,recipient_phone,postal_code,address_line1,address_line2,created_at)
        VALUES (?,?,'SUBSCRIPTION','PAYMENT_PENDING',?,0,0,?,?,?,?,?,?,?)\
        """,
        orderNumber,
        longValue(subscription, "member_id"),
        total,
        total,
        shipping.get("recipient_name"),
        shipping.get("recipient_phone"),
        shipping.get("postal_code"),
        shipping.get("address_line1"),
        shipping.get("address_line2"),
        timestamp);
    long orderId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update(
        """
        INSERT INTO subscription_order_context(order_id,subscription_id,schedule_id,effective_snapshot_id,source_plan_version_id,scheduled_date)
        VALUES (?,?,?,?,?,?)\
        """,
        orderId,
        longValue(subscription, "id"),
        longValue(schedule, "id"),
        longValue(effectiveSnapshot, "id"),
        longValue(effectiveSnapshot, "source_plan_version_id"),
        dateValue(schedule, "scheduled_date"));
    String providerOrderId = "TOSS-SUB-" + UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO payments(order_id,type,provider,status,amount,provider_order_id,idempotency_key,attempt_no,requested_at,created_at)
        VALUES (?,'BILLING','TOSS','READY',?,?,?,?,?,?)\
        """,
        orderId,
        total,
        providerOrderId,
        "billing-" + UUID.randomUUID(),
        1,
        timestamp,
        timestamp);
    long paymentId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    List<Map<String, Object>> items =
        jdbc.queryForList(
            """
            SELECT item.sku_id,item.quantity,sku.sku_code,sku.name AS sku_name,sku.price,product.name AS product_name
            FROM subscription_snapshot_items item JOIN skus sku ON sku.id=item.sku_id
            JOIN products product ON product.id=sku.product_id WHERE item.snapshot_id=? ORDER BY item.sku_id\
            """,
            longValue(effectiveSnapshot, "id"));
    if (items.size() != snapshotItems.size())
      throw new IllegalStateException("Subscription snapshot SKU is missing");
    for (Map<String, Object> item : items) {
      int quantity = intValue(item, "quantity");
      reserveInventory(longValue(item, "sku_id"), quantity, paymentId, timestamp);
      BigDecimal unitPrice = (BigDecimal) item.get("price");
      jdbc.update(
          """
          INSERT INTO order_items(order_id,sku_id,snapshot_quality,sku_code_snapshot,product_name_snapshot,sku_name_snapshot,unit_price,quantity,line_amount)
          VALUES (?,?,'FULL',?,?,?,?,?,?)\
          """,
          orderId,
          longValue(item, "sku_id"),
          item.get("sku_code"),
          item.get("product_name"),
          item.get("sku_name"),
          unitPrice,
          quantity,
          unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }
    for (Map<String, Object> addon : addOns) {
      int quantity = intValue(addon, "quantity");
      BigDecimal unitPrice = decimalValue(addon, "unit_price_krw");
      reserveInventory(longValue(addon, "sku_id"), quantity, paymentId, timestamp);
      jdbc.update(
          """
          INSERT INTO order_items(order_id,sku_id,snapshot_quality,sku_code_snapshot,product_name_snapshot,sku_name_snapshot,unit_price,quantity,line_amount)
          VALUES (?,?,'FULL',?,?,?,?,?,?)\
          """,
          orderId,
          longValue(addon, "sku_id"),
          addon.get("sku_code"),
          addon.get("product_name"),
          addon.get("sku_name"),
          unitPrice,
          quantity,
          unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }
    return orderId;
  }

  private boolean hasUnavailableStock(
      List<Map<String, Object>> baseItems, List<Map<String, Object>> addOns) {
    Map<Long, Integer> quantities = new java.util.TreeMap<>();
    for (Map<String, Object> item : baseItems)
      quantities.merge(longValue(item, "sku_id"), intValue(item, "quantity"), Integer::sum);
    for (Map<String, Object> addon : addOns) {
      long skuId = longValue(addon, "sku_id");
      if (quantities.containsKey(skuId))
        throw new IllegalStateException("Base plan SKU conflicts with Add-on SKU");
      quantities.put(skuId, intValue(addon, "quantity"));
    }
    for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
      Integer available =
          jdbc.query(
              "SELECT available_quantity FROM inventories WHERE sku_id=? FOR UPDATE",
              rs -> rs.next() ? rs.getInt(1) : null,
              entry.getKey());
      if (available == null || available < entry.getValue()) return true;
    }
    return false;
  }

  private BigDecimal addOnTotal(List<Map<String, Object>> addOns) {
    return addOns.stream()
        .map(
            addon ->
                decimalValue(addon, "unit_price_krw")
                    .multiply(BigDecimal.valueOf(intValue(addon, "quantity"))))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static boolean isDueStatus(Map<String, Object> schedule) {
    return "SCHEDULED".equals(schedule.get("status"))
        || ("HELD".equals(schedule.get("status"))
            && "ORDER_STOCK_UNAVAILABLE".equals(schedule.get("hold_reason")));
  }

  private void reserveInventory(long skuId, int quantity, long paymentId, LocalDateTime timestamp) {
    Map<String, Object> inventory =
        one(
                "SELECT available_quantity,reserved_quantity,version FROM inventories WHERE"
                    + " sku_id=?",
                skuId)
            .orElseThrow(() -> new IllegalStateException("Inventory is missing"));
    long available = longValue(inventory, "available_quantity");
    long reserved = longValue(inventory, "reserved_quantity");
    long version = longValue(inventory, "version");
    if (available < quantity
        || jdbc.update(
                """
                UPDATE inventories SET available_quantity=available_quantity-?,reserved_quantity=reserved_quantity+?,version=version+1
                WHERE sku_id=? AND version=? AND available_quantity>=?\
                """,
                quantity,
                quantity,
                skuId,
                version,
                quantity)
            != 1) {
      throw new IllegalStateException("Inventory reservation conflict");
    }
    jdbc.update(
        """
        INSERT INTO inventory_movements(sku_id,payment_id,type,quantity,available_before,available_after,reserved_before,reserved_after,created_at)
        VALUES (?,?,'RESERVE',?,?,?,?,?,?)\
        """,
        skuId,
        paymentId,
        quantity,
        available,
        available - quantity,
        reserved,
        reserved + quantity,
        timestamp);
  }

  private void hold(long scheduleId, String reason) {
    jdbc.update(
        "UPDATE subscription_schedules SET status='HELD',hold_reason=? WHERE id=? AND"
            + " (status='SCHEDULED' OR (status='HELD' AND hold_reason='ORDER_STOCK_UNAVAILABLE'))",
        reason,
        scheduleId);
  }

  static LocalDate firstFutureDate(
      LocalDate scheduledDate, int deliveryCycleWeeks, LocalDate today) {
    LocalDate next = scheduledDate;
    do {
      next = next.plusWeeks(deliveryCycleWeeks);
    } while (!next.isAfter(today));
    return next;
  }

  private Map<String, Object> snapshot(long subscriptionId, long snapshotId) {
    return one(
            "SELECT id,source_plan_version_id,package_total_krw,delivery_cycle_weeks "
                + "FROM subscription_snapshots WHERE id=? AND subscription_id=?",
            snapshotId,
            subscriptionId)
        .orElseThrow(() -> new IllegalStateException("Subscription snapshot is missing"));
  }

  private boolean orderExists(long scheduleId) {
    return !jdbc.queryForList(
            "SELECT id FROM subscription_orders WHERE schedule_id=? FOR UPDATE", scheduleId)
        .isEmpty();
  }

  private Optional<Map<String, Object>> one(String sql, Object... arguments) {
    List<Map<String, Object>> rows = jdbc.queryForList(sql, arguments);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
  }

  private LocalDate today() {
    return LocalDate.ofInstant(clock.instant(), SEOUL);
  }

  private static LocalDate dateValue(Map<String, Object> row, String key) {
    Object value = row.get(key);
    if (value instanceof LocalDate localDate) return localDate;
    if (value instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate();
    if (value instanceof java.sql.Timestamp timestamp) {
      return timestamp.toLocalDateTime().toLocalDate();
    }
    throw new IllegalArgumentException(
        "Unsupported date value type: " + (value == null ? "null" : value.getClass().getName()));
  }

  private static long longValue(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).longValue();
  }

  private static BigDecimal decimalValue(Map<String, Object> row, String key) {
    Object value = row.get(key);
    return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
  }

  private static int intValue(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).intValue();
  }

  private static String failureCategory(RuntimeException exception) {
    if (exception instanceof DataIntegrityViolationException) {
      return "DATA_INTEGRITY";
    }
    if (exception instanceof DataAccessException) {
      return "DATABASE";
    }
    if (exception instanceof IllegalStateException) {
      return "INVARIANT";
    }
    return "UNEXPECTED";
  }

  private enum ProcessingOutcome {
    CREATED,
    NO_OP
  }

  private record Candidate(long subscriptionId, long scheduleId) {}

}
