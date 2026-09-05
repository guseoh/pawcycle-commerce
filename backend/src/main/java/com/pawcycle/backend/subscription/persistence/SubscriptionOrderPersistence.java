package com.pawcycle.backend.subscription.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionOrderPersistence {
  private final JdbcTemplate jdbc;

  public SubscriptionOrderPersistence(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public static final String UPDATE_SCHEDULE_EFFECTIVE_SQL =
      "UPDATE subscription_schedules SET"
          + " effective_snapshot_id=?,status='SCHEDULED',hold_reason=NULL WHERE id=?";

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

  public List<ExistingOrderRow> lockExistingOrders(long scheduleId) {
    return jdbc.query(
        "SELECT id FROM subscription_orders WHERE schedule_id=? FOR UPDATE",
        (rs, row) -> new ExistingOrderRow(rs.getLong("id")),
        scheduleId);
  }

  public Optional<SnapshotRow> findSnapshot(long snapshotId, long subscriptionId) {
    return jdbc
        .query(
            "SELECT id,source_plan_version_id,package_total_krw,delivery_cycle_weeks "
                + "FROM subscription_snapshots WHERE id=? AND subscription_id=?",
            (rs, row) ->
                new SnapshotRow(
                    rs.getLong("id"),
                    rs.getLong("source_plan_version_id"),
                    rs.getBigDecimal("package_total_krw"),
                    rs.getInt("delivery_cycle_weeks")),
            snapshotId,
            subscriptionId)
        .stream()
        .findFirst();
  }

  public int holdSchedule(String reason, long scheduleId) {
    return jdbc.update(
        "UPDATE subscription_schedules SET status='HELD',hold_reason=? WHERE id=? AND"
            + " (status='SCHEDULED' OR (status='HELD' AND hold_reason='ORDER_STOCK_UNAVAILABLE'))",
        reason,
        scheduleId);
  }

  public int insertReservationMovement(
      long skuId,
      long paymentId,
      int quantity,
      long availableBefore,
      long availableAfter,
      long reservedBefore,
      long reservedAfter,
      LocalDateTime createdAt) {
    return jdbc.update(
        """
        INSERT INTO inventory_movements(sku_id,payment_id,type,quantity,available_before,available_after,reserved_before,reserved_after,created_at)
        VALUES (?,?,'RESERVE',?,?,?,?,?,?)\
        """,
        skuId,
        paymentId,
        quantity,
        availableBefore,
        availableAfter,
        reservedBefore,
        reservedAfter,
        createdAt);
  }

  public int reserveInventory(
      int quantity, int reservedQuantity, long skuId, long expectedVersion, int minimumQuantity) {
    return jdbc.update(
        """
        UPDATE inventories SET available_quantity=available_quantity-?,reserved_quantity=reserved_quantity+?,version=version+1
        WHERE sku_id=? AND version=? AND available_quantity>=?\
        """,
        quantity,
        reservedQuantity,
        skuId,
        expectedVersion,
        minimumQuantity);
  }

  public Optional<InventoryRow> findInventory(long skuId) {
    return jdbc
        .query(
            "SELECT available_quantity,reserved_quantity,version FROM inventories WHERE"
                + " sku_id=?",
            (rs, row) ->
                new InventoryRow(
                    rs.getLong("available_quantity"),
                    rs.getLong("reserved_quantity"),
                    rs.getLong("version")),
            skuId)
        .stream()
        .findFirst();
  }

  public Integer lockAvailableQuantity(long skuId) {
    return jdbc.query(
        "SELECT available_quantity FROM inventories WHERE sku_id=? FOR UPDATE",
        rs -> rs.next() ? rs.getInt(1) : null,
        skuId);
  }

  public int insertOrderItem(
      long orderId,
      long skuId,
      String skuCode,
      String productName,
      String skuName,
      BigDecimal unitPrice,
      int quantity,
      BigDecimal lineAmount) {
    return jdbc.update(
        """
        INSERT INTO order_items(order_id,sku_id,snapshot_quality,sku_code_snapshot,product_name_snapshot,sku_name_snapshot,unit_price,quantity,line_amount)
        VALUES (?,?,'FULL',?,?,?,?,?,?)\
        """,
        orderId,
        skuId,
        skuCode,
        productName,
        skuName,
        unitPrice,
        quantity,
        lineAmount);
  }

  public List<PricedItem> findPricedSnapshotItems(long snapshotId) {
    return jdbc.query(
        """
        SELECT item.sku_id,item.quantity,sku.sku_code,sku.name AS sku_name,sku.price,product.name AS product_name
        FROM subscription_snapshot_items item JOIN skus sku ON sku.id=item.sku_id
        JOIN products product ON product.id=sku.product_id WHERE item.snapshot_id=? ORDER BY item.sku_id\
        """,
        (rs, row) ->
            new PricedItem(
                rs.getLong("sku_id"),
                rs.getInt("quantity"),
                rs.getString("sku_code"),
                rs.getString("sku_name"),
                rs.getBigDecimal("price"),
                rs.getString("product_name")),
        snapshotId);
  }

  public Long lastInsertedId() {
    return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }

  public int insertBillingPayment(
      long orderId,
      BigDecimal amount,
      String providerOrderId,
      String idempotencyKey,
      int attempt,
      LocalDateTime requestedAt,
      LocalDateTime createdAt) {
    return jdbc.update(
        """
        INSERT INTO payments(order_id,type,provider,status,amount,provider_order_id,idempotency_key,attempt_no,requested_at,created_at)
        VALUES (?,'BILLING','TOSS','READY',?,?,?,?,?,?)\
        """,
        orderId,
        amount,
        providerOrderId,
        idempotencyKey,
        attempt,
        requestedAt,
        createdAt);
  }

  public int insertOrderContext(
      long orderId,
      long subscriptionId,
      long scheduleId,
      long snapshotId,
      long planVersionId,
      LocalDate scheduledDate) {
    return jdbc.update(
        """
        INSERT INTO subscription_order_context(order_id,subscription_id,schedule_id,effective_snapshot_id,source_plan_version_id,scheduled_date)
        VALUES (?,?,?,?,?,?)\
        """,
        orderId,
        subscriptionId,
        scheduleId,
        snapshotId,
        planVersionId,
        scheduledDate);
  }

  public int insertOrder(
      String orderNumber,
      long memberId,
      BigDecimal originalAmount,
      BigDecimal paymentAmount,
      String recipientName,
      String recipientPhone,
      String postalCode,
      String addressLine1,
      String addressLine2,
      LocalDateTime createdAt) {
    return jdbc.update(
        """
        INSERT INTO orders(order_number,member_id,source,status,original_amount,discount_amount,shipping_fee,payment_amount,
         recipient_name,recipient_phone,postal_code,address_line1,address_line2,created_at)
        VALUES (?,?,'SUBSCRIPTION','PAYMENT_PENDING',?,0,0,?,?,?,?,?,?,?)\
        """,
        orderNumber,
        memberId,
        originalAmount,
        paymentAmount,
        recipientName,
        recipientPhone,
        postalCode,
        addressLine1,
        addressLine2,
        createdAt);
  }

  public int insertShippingSnapshot(
      long subscriptionId,
      String recipientName,
      String recipientPhone,
      String postalCode,
      String addressLine1,
      String addressLine2,
      LocalDateTime updatedAt) {
    return jdbc.update(
        """
        INSERT INTO subscription_shipping_snapshots(subscription_id,recipient_name,recipient_phone,postal_code,address_line1,address_line2,updated_at)
        VALUES (?,?,?,?,?,?,?)\
        """,
        subscriptionId,
        recipientName,
        recipientPhone,
        postalCode,
        addressLine1,
        addressLine2,
        updatedAt);
  }

  public Optional<ShippingRow> lockDefaultAddress(long memberId) {
    return jdbc
        .query(
            """
            SELECT address.recipient_name,address.recipient_phone,address.postal_code,address.address_line1,address.address_line2
            FROM members member JOIN member_addresses address ON address.id=member.default_address_id
            WHERE member.id=? FOR UPDATE\
            """,
            (rs, row) ->
                new ShippingRow(
                    rs.getString("recipient_name"),
                    rs.getString("recipient_phone"),
                    rs.getString("postal_code"),
                    rs.getString("address_line1"),
                    rs.getString("address_line2")),
            memberId)
        .stream()
        .findFirst();
  }

  public Optional<ShippingRow> lockShippingSnapshot(long subscriptionId) {
    return jdbc
        .query(
            "SELECT recipient_name,recipient_phone,postal_code,address_line1,address_line2 "
                + "FROM subscription_shipping_snapshots WHERE subscription_id=? FOR UPDATE",
            (rs, row) ->
                new ShippingRow(
                    rs.getString("recipient_name"),
                    rs.getString("recipient_phone"),
                    rs.getString("postal_code"),
                    rs.getString("address_line1"),
                    rs.getString("address_line2")),
            subscriptionId)
        .stream()
        .findFirst();
  }

  public int incrementVersion(long subscriptionId, long expectedVersion) {
    return jdbc.update(
        "UPDATE subscriptions SET version=version+1 WHERE id=? AND version=?",
        subscriptionId,
        expectedVersion);
  }

  public int insertFutureSchedule(long subscriptionId, LocalDate scheduledDate) {
    return jdbc.update(
        "INSERT INTO subscription_schedules("
            + "subscription_id,scheduled_date,status,effective_snapshot_id"
            + ") VALUES (?,?,'SCHEDULED',NULL)",
        subscriptionId,
        scheduledDate);
  }

  public List<FutureScheduleRow> lockFutureSchedules(long subscriptionId, LocalDate today) {
    return jdbc.query(
        "SELECT id,scheduled_date FROM subscription_schedules "
            + "WHERE subscription_id=? AND status='SCHEDULED' AND scheduled_date>? "
            + "ORDER BY scheduled_date,id FOR UPDATE",
        (rs, row) ->
            new FutureScheduleRow(rs.getLong("id"), rs.getDate("scheduled_date").toLocalDate()),
        subscriptionId,
        today);
  }

  public int deletePendingChange(long subscriptionId, long snapshotId, long scheduleId) {
    return jdbc.update(
        "DELETE FROM pending_plan_changes "
            + "WHERE subscription_id=? AND snapshot_id=? AND target_schedule_id=?",
        subscriptionId,
        snapshotId,
        scheduleId);
  }

  public int promoteSnapshot(
      long snapshotId,
      int cycleWeeks,
      long subscriptionId,
      long expectedSnapshotId,
      int expectedCycleWeeks) {
    return jdbc.update(
        "UPDATE subscriptions SET current_snapshot_id=?,delivery_cycle_weeks=? "
            + "WHERE id=? AND current_snapshot_id=? AND delivery_cycle_weeks=?",
        snapshotId,
        cycleWeeks,
        subscriptionId,
        expectedSnapshotId,
        expectedCycleWeeks);
  }

  public int deleteReminder(long scheduleId) {
    return jdbc.update(
        "DELETE FROM notifications WHERE type='SUBSCRIPTION_DELIVERY_REMINDER' AND"
            + " reference_type='SCHEDULE' AND reference_id=?",
        scheduleId);
  }

  public int setEffectiveSnapshot(long snapshotId, long scheduleId) {
    return jdbc.update(UPDATE_SCHEDULE_EFFECTIVE_SQL, snapshotId, scheduleId);
  }

  public int deleteScheduleAddOns(long scheduleId) {
    return jdbc.update("DELETE FROM subscription_schedule_addons WHERE schedule_id=?", scheduleId);
  }

  public int insertSubscriptionOrderAddOn(
      long orderId, long skuId, int quantity, BigDecimal price) {
    return jdbc.update(
        "INSERT INTO"
            + " subscription_order_addon_items(subscription_order_id,sku_id,quantity,unit_price_krw)"
            + " VALUES (?,?,?,?)",
        orderId,
        skuId,
        quantity,
        price);
  }

  public int insertSubscriptionOrderItem(long orderId, long skuId, int quantity) {
    return jdbc.update(
        "INSERT INTO subscription_order_items(order_id,sku_id,quantity) VALUES (?,?,?)",
        orderId,
        skuId,
        quantity);
  }

  public int insertSubscriptionOrder(
      long memberId,
      long subscriptionId,
      long scheduleId,
      long snapshotId,
      long planVersionId,
      LocalDate scheduledDate,
      LocalDateTime processedAt,
      BigDecimal total) {
    return jdbc.update(
        "INSERT INTO"
            + " subscription_orders(member_id,subscription_id,schedule_id,effective_snapshot_id,source_plan_version_id,scheduled_date,processed_at,package_total_krw,status)"
            + " VALUES (?,?,?,?,?,?,?,?,'CREATED')",
        memberId,
        subscriptionId,
        scheduleId,
        snapshotId,
        planVersionId,
        scheduledDate,
        processedAt,
        total);
  }

  public List<AddOnRow> lockAddOns(long scheduleId) {
    return jdbc.query(
        """
        SELECT addon.sku_id,addon.quantity,addon.unit_price_krw,sku.sku_code,sku.name AS sku_name,
               sku.status AS sku_status,product.name AS product_name,product.display_status,
               category.active AS category_active,brand.active AS brand_active
        FROM subscription_schedule_addons addon JOIN skus sku ON sku.id=addon.sku_id
        JOIN products product ON product.id=sku.product_id JOIN categories category ON category.id=product.category_id
        JOIN brands brand ON brand.id=product.brand_id
        WHERE addon.schedule_id=? ORDER BY addon.sku_id FOR UPDATE\
        """,
        (rs, row) ->
            new AddOnRow(
                rs.getLong("sku_id"),
                rs.getInt("quantity"),
                rs.getBigDecimal("unit_price_krw"),
                rs.getString("sku_code"),
                rs.getString("sku_name"),
                rs.getString("sku_status"),
                rs.getString("product_name"),
                rs.getString("display_status"),
                rs.getBoolean("category_active"),
                rs.getBoolean("brand_active")),
        scheduleId);
  }

  public List<SnapshotItem> findSnapshotItems(long snapshotId) {
    return jdbc.query(
        "SELECT sku_id,quantity FROM subscription_snapshot_items "
            + "WHERE snapshot_id=? ORDER BY sku_id",
        (rs, row) -> new SnapshotItem(rs.getLong("sku_id"), rs.getInt("quantity")),
        snapshotId);
  }

  public Optional<PendingChangeRow> lockPendingChange(long subscriptionId) {
    return jdbc
        .query(
            "SELECT snapshot_id,target_schedule_id FROM pending_plan_changes "
                + "WHERE subscription_id=? FOR UPDATE",
            (rs, row) ->
                new PendingChangeRow(rs.getLong("snapshot_id"), rs.getLong("target_schedule_id")),
            subscriptionId)
        .stream()
        .findFirst();
  }

  public Optional<BillingMethodRow> lockBillingMethod(long memberId) {
    return jdbc
        .query(
            "SELECT id FROM billing_payment_methods WHERE member_id=? AND status='ACTIVE' FOR"
                + " UPDATE",
            (rs, row) -> new BillingMethodRow(rs.getLong("id")),
            memberId)
        .stream()
        .findFirst();
  }

  public Optional<ScheduleRow> lockSchedule(long scheduleId) {
    return jdbc
        .query(
            "SELECT id,subscription_id,scheduled_date,status,hold_reason,effective_snapshot_id "
                + "FROM subscription_schedules WHERE id=? FOR UPDATE",
            (rs, row) ->
                new ScheduleRow(
                    rs.getLong("id"),
                    rs.getLong("subscription_id"),
                    rs.getDate("scheduled_date").toLocalDate(),
                    rs.getString("status"),
                    rs.getString("hold_reason"),
                    rs.getObject("effective_snapshot_id", Long.class)),
            scheduleId)
        .stream()
        .findFirst();
  }

  public Optional<SubscriptionRow> lockSubscription(long subscriptionId) {
    return jdbc
        .query(
            "SELECT"
                + " id,member_id,status,runtime_managed,version,current_snapshot_id,delivery_cycle_weeks"
                + " FROM subscriptions WHERE id=? FOR UPDATE",
            (rs, row) ->
                new SubscriptionRow(
                    rs.getLong("id"),
                    rs.getLong("member_id"),
                    rs.getString("status"),
                    rs.getBoolean("runtime_managed"),
                    rs.getLong("version"),
                    rs.getLong("current_snapshot_id"),
                    rs.getInt("delivery_cycle_weeks")),
            subscriptionId)
        .stream()
        .findFirst();
  }

  public List<Candidate> findDueCandidates(
      LocalDate today, LocalDate repeatedToday, int batchSize) {
    return jdbc.query(
        FIND_DUE_CANDIDATES_SQL,
        (rs, row) -> new Candidate(rs.getLong("subscription_id"), rs.getLong("schedule_id")),
        today,
        repeatedToday,
        batchSize);
  }

  public record Candidate(long subscriptionId, long scheduleId) {}

  public record SubscriptionRow(
      long id,
      long memberId,
      String status,
      boolean runtimeManaged,
      long version,
      long currentSnapshotId,
      int deliveryCycleWeeks) {}

  public record ScheduleRow(
      long id,
      long subscriptionId,
      LocalDate scheduledDate,
      String status,
      String holdReason,
      Long effectiveSnapshotId) {}

  public record BillingMethodRow(long id) {}

  public record PendingChangeRow(long snapshotId, long targetScheduleId) {}

  public record SnapshotItem(long skuId, int quantity) {}

  public record AddOnRow(
      long skuId,
      int quantity,
      BigDecimal unitPriceKrw,
      String skuCode,
      String skuName,
      String skuStatus,
      String productName,
      String displayStatus,
      boolean categoryActive,
      boolean brandActive) {}

  public record FutureScheduleRow(long id, LocalDate scheduledDate) {}

  public record ShippingRow(
      String recipientName,
      String recipientPhone,
      String postalCode,
      String addressLine1,
      String addressLine2) {}

  public record PricedItem(
      long skuId,
      int quantity,
      String skuCode,
      String skuName,
      BigDecimal price,
      String productName) {}

  public record InventoryRow(long availableQuantity, long reservedQuantity, long version) {}

  public record SnapshotRow(
      long id, long sourcePlanVersionId, BigDecimal packageTotalKrw, int deliveryCycleWeeks) {}

  public record ExistingOrderRow(long id) {}
}
