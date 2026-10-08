package com.pawcycle.backend.subscription.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.EntityManager;
import com.pawcycle.backend.commerce.notification.persistence.QNotificationEntity;
import jakarta.persistence.LockModeType;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.pawcycle.backend.commerce.order.domain.CommerceOrderEntity;
import com.pawcycle.backend.commerce.order.domain.CommerceOrderItemEntity;
import com.pawcycle.backend.commerce.order.persistence.SubscriptionOrderContextEntity;
import com.pawcycle.backend.commerce.order.persistence.SubscriptionShippingSnapshotEntity;
import com.pawcycle.backend.commerce.order.persistence.QSubscriptionOrderContextEntity;
import com.pawcycle.backend.commerce.order.persistence.QSubscriptionShippingSnapshotEntity;
import com.pawcycle.backend.commerce.payment.domain.PaymentEntity;
import com.pawcycle.backend.commerce.payment.domain.QPaymentEntity;
import com.pawcycle.backend.commerce.inventory.persistence.InventoryMovementEntity;
import com.pawcycle.backend.commerce.inventory.persistence.QInventoryEntity;
import com.pawcycle.backend.commerce.billing.persistence.QBillingPaymentMethodEntity;
import com.pawcycle.backend.catalog.sku.domain.QSku;
import com.pawcycle.backend.catalog.product.domain.QProduct;
import org.springframework.stereotype.Repository;

@Repository
public class SubscriptionOrderPersistence {
  private final SubscriptionNativeSql nativeSql;
  private final EntityManager entities;
  private final JPAQueryFactory queries;
  private final QSubscriptionEntity subscription = new QSubscriptionEntity("subscription");
  private final QSubscriptionScheduleEntity schedule = new QSubscriptionScheduleEntity("schedule");
  private final QPendingPlanChangeEntity pending = new QPendingPlanChangeEntity("pending");
  private final QInventoryEntity inventory = new QInventoryEntity("inventory");

  public SubscriptionOrderPersistence(EntityManager entities) {
    this.nativeSql = new SubscriptionNativeSql(entities);
    this.entities = entities;
    this.queries = new JPAQueryFactory(entities);
  }

  public static final String UPDATE_SCHEDULE_EFFECTIVE_SQL =
      "UPDATE subscription_schedules SET"
          + " effective_snapshot_id=?,status='SCHEDULED',hold_reason=NULL WHERE id=?";

  public List<ExistingOrderRow> lockExistingOrders(long scheduleId) {
    var order = new QSubscriptionOrderEntity("existing");
    return queries.select(Projections.constructor(ExistingOrderRow.class, order.id)).from(order)
        .where(order.scheduleId.eq(scheduleId)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetch();
  }

  public Optional<SnapshotRow> findSnapshot(long snapshotId, long subscriptionId) {
    var snapshot = new QSubscriptionSnapshotEntity("snapshot");
    var row = queries.select(snapshot.id, snapshot.planVersionId, snapshot.packagePriceKrw, snapshot.deliveryCycleWeeks)
        .from(snapshot).where(snapshot.id.eq(snapshotId), snapshot.subscriptionId.eq(subscriptionId)).fetchOne();
    return Optional.ofNullable(row).map(value -> new SnapshotRow(value.get(snapshot.id), value.get(snapshot.planVersionId),
        BigDecimal.valueOf(value.get(snapshot.packagePriceKrw)), value.get(snapshot.deliveryCycleWeeks)));
  }

  public int holdSchedule(String reason, long scheduleId) {
    return Math.toIntExact(queries.update(schedule).set(schedule.status, "HELD").set(schedule.holdReason, reason)
        .where(schedule.id.eq(scheduleId), eligible(schedule)).execute());
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
    var row = InventoryMovementEntity.subscriptionReservation(skuId, paymentId, quantity,
        Math.toIntExact(availableBefore), Math.toIntExact(availableAfter), Math.toIntExact(reservedBefore), Math.toIntExact(reservedAfter),
        java.sql.Timestamp.from(createdAt.toInstant(java.time.ZoneOffset.UTC)));
    entities.persist(row);
    entities.flush();
    entities.detach(row);
    return 1;
  }

  public int reserveInventory(
      int quantity, int reservedQuantity, long skuId, long expectedVersion, int minimumQuantity) {
    return Math.toIntExact(queries.update(inventory)
        .set(inventory.availableQuantity, inventory.availableQuantity.subtract(quantity))
        .set(inventory.reservedQuantity, inventory.reservedQuantity.add(reservedQuantity))
        .set(inventory.version, inventory.version.add(1))
        .where(inventory.skuId.eq(skuId), inventory.version.eq(expectedVersion), inventory.availableQuantity.goe(minimumQuantity)).execute());
  }

  public Optional<InventoryRow> findInventory(long skuId) {
    return Optional.ofNullable(queries.select(Projections.constructor(InventoryRow.class,
            inventory.availableQuantity.longValue(), inventory.reservedQuantity.longValue(), inventory.version))
        .from(inventory).where(inventory.skuId.eq(skuId)).fetchOne());
  }

  public Integer lockAvailableQuantity(long skuId) {
    return queries.select(inventory.availableQuantity).from(inventory).where(inventory.skuId.eq(skuId))
        .setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne();
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
    var row = new CommerceOrderItemEntity(orderId, skuId, skuCode, productName, skuName, unitPrice, quantity, lineAmount);
    entities.persist(row);
    entities.flush();
    entities.detach(row);
    return 1;
  }

  public List<PricedItem> findPricedSnapshotItems(long snapshotId) {
    var item = new QSubscriptionReadRows_SnapshotItem("item");
    var sku = new QSku("sku");
    var product = new QProduct("product");
    return queries.select(Projections.constructor(PricedItem.class, item.skuId, item.quantity, sku.skuCode, sku.name, sku.price, product.name))
        .from(item).join(sku).on(sku.id.eq(item.skuId)).join(sku.product, product)
        .where(item.snapshotId.eq(snapshotId)).orderBy(item.skuId.asc()).fetch();
  }

  public Long lastInsertedId() {
    return nativeSql.query("SELECT LAST_INSERT_ID()", Long.class).uniqueResult();
  }

  public int insertBillingPayment(
      long orderId,
      BigDecimal amount,
      String providerOrderId,
      String idempotencyKey,
      int attempt,
      LocalDateTime requestedAt,
      LocalDateTime createdAt) {
    var row = PaymentEntity.billing(orderId, amount, providerOrderId, idempotencyKey, attempt,
        SubscriptionJdbcTime.forUtcCalendar(requestedAt), SubscriptionJdbcTime.forUtcCalendar(createdAt));
    entities.persist(row);
    entities.flush();
    entities.detach(row);
    return 1;
  }

  public int insertOrderContext(
      long orderId,
      long subscriptionId,
      long scheduleId,
      long snapshotId,
      long planVersionId,
      LocalDate scheduledDate) {
    var row = new SubscriptionOrderContextEntity(orderId, subscriptionId, scheduleId, snapshotId, planVersionId, scheduledDate);
    entities.persist(row);
    entities.flush();
    entities.detach(row);
    return 1;
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
    var row = CommerceOrderEntity.subscription(orderNumber, memberId, originalAmount, paymentAmount,
        recipientName, recipientPhone, postalCode, addressLine1, addressLine2, SubscriptionJdbcTime.forUtcCalendar(createdAt));
    entities.persist(row);
    entities.flush();
    entities.detach(row);
    return 1;
  }

  public int insertShippingSnapshot(
      long subscriptionId,
      String recipientName,
      String recipientPhone,
      String postalCode,
      String addressLine1,
      String addressLine2,
      LocalDateTime updatedAt) {
    var row = new SubscriptionShippingSnapshotEntity(subscriptionId, recipientName, recipientPhone,
        postalCode, addressLine1, addressLine2, SubscriptionJdbcTime.forUtcCalendar(updatedAt));
    entities.persist(row);
    entities.flush();
    entities.detach(row);
    return 1;
  }

  public Optional<ShippingRow> lockDefaultAddress(long memberId) {
    return nativeSql.query("""
        SELECT address.recipient_name,address.recipient_phone,address.postal_code,address.address_line1,address.address_line2
        FROM members member JOIN member_addresses address ON address.id=member.default_address_id
        WHERE member.id=? FOR UPDATE
        """, ShippingRow.class, memberId).getResultList().stream().findFirst();
  }

  public Optional<ShippingRow> lockShippingSnapshot(long subscriptionId) {
    var shipping = new QSubscriptionShippingSnapshotEntity("shipping");
    return Optional.ofNullable(queries.select(Projections.constructor(ShippingRow.class,
            shipping.recipientName, shipping.recipientPhone, shipping.postalCode, shipping.addressLine1, shipping.addressLine2))
        .from(shipping).where(shipping.subscriptionId.eq(subscriptionId)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne());
  }

  public int incrementVersion(long subscriptionId, long expectedVersion) {
    return Math.toIntExact(queries.update(subscription).set(subscription.version, subscription.version.add(1))
        .where(subscription.id.eq(subscriptionId), subscription.version.eq(expectedVersion)).execute());
  }

  public int insertFutureSchedule(long subscriptionId, LocalDate scheduledDate) {
    var row = new SubscriptionScheduleEntity(subscriptionId, scheduledDate);
    entities.persist(row);
    entities.flush();
    entities.detach(row);
    return 1;
  }

  public List<FutureScheduleRow> lockFutureSchedules(long subscriptionId, LocalDate today) {
    return queries.select(Projections.constructor(FutureScheduleRow.class, schedule.id, schedule.scheduledDate))
        .from(schedule).where(schedule.subscriptionId.eq(subscriptionId), schedule.status.eq("SCHEDULED"), schedule.scheduledDate.gt(today))
        .orderBy(schedule.scheduledDate.asc(), schedule.id.asc()).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetch();
  }

  public int deletePendingChange(long subscriptionId, long snapshotId, long scheduleId) {
    return Math.toIntExact(queries.delete(pending).where(pending.subscriptionId.eq(subscriptionId),
        pending.snapshotId.eq(snapshotId), pending.targetScheduleId.eq(scheduleId)).execute());
  }

  public int promoteSnapshot(
      long snapshotId,
      int cycleWeeks,
      long subscriptionId,
      long expectedSnapshotId,
      int expectedCycleWeeks) {
    return Math.toIntExact(queries.update(subscription).set(subscription.currentSnapshotId, snapshotId)
        .set(subscription.deliveryCycleWeeks, cycleWeeks).where(subscription.id.eq(subscriptionId),
            subscription.currentSnapshotId.eq(expectedSnapshotId), subscription.deliveryCycleWeeks.eq(expectedCycleWeeks)).execute());
  }

  public int deleteReminder(long scheduleId) {
    var notification = new QNotificationEntity("notification");
    return Math.toIntExact(queries.delete(notification).where(notification.type.eq("SUBSCRIPTION_DELIVERY_REMINDER"),
        notification.referenceType.eq("SCHEDULE"), notification.referenceId.eq(scheduleId)).execute());
  }

  public int setEffectiveSnapshot(long snapshotId, long scheduleId) {
    return Math.toIntExact(queries.update(schedule).set(schedule.effectiveSnapshotId, snapshotId)
        .set(schedule.status, "SCHEDULED").setNull(schedule.holdReason).where(schedule.id.eq(scheduleId)).execute());
  }

  public int deleteScheduleAddOns(long scheduleId) {
    var addon = new QSubscriptionScheduleAddonEntity("addon");
    return Math.toIntExact(queries.delete(addon).where(addon.scheduleId.eq(scheduleId)).execute());
  }

  public int insertSubscriptionOrderAddOn(
      long orderId, long skuId, int quantity, BigDecimal price) {
    var row = new SubscriptionOrderAddonItemEntity(orderId, skuId, quantity, price);
    entities.persist(row);
    entities.flush();
    entities.detach(row);
    return 1;
  }

  public int insertSubscriptionOrderItem(long orderId, long skuId, int quantity) {
    var row = new SubscriptionOrderItemEntity(orderId, skuId, quantity);
    entities.persist(row);
    entities.flush();
    entities.detach(row);
    return 1;
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
    var row = new SubscriptionOrderEntity(memberId, subscriptionId, scheduleId,
        snapshotId, planVersionId, scheduledDate, processedAt, total);
    entities.persist(row);
    entities.flush();
    entities.detach(row);
    return 1;
  }

  public List<AddOnRow> lockAddOns(long scheduleId) {
    return nativeSql.query("""
        SELECT addon.sku_id,addon.quantity,addon.unit_price_krw,sku.sku_code,sku.name AS sku_name,
               sku.status AS sku_status,product.name AS product_name,product.display_status,
               category.active AS category_active,brand.active AS brand_active
        FROM subscription_schedule_addons addon JOIN skus sku ON sku.id=addon.sku_id
        JOIN products product ON product.id=sku.product_id JOIN categories category ON category.id=product.category_id
        JOIN brands brand ON brand.id=product.brand_id
        WHERE addon.schedule_id=? ORDER BY addon.sku_id FOR UPDATE
        """, AddOnRow.class, scheduleId)
        .addScalar("sku_id", Long.class).addScalar("quantity", Integer.class).addScalar("unit_price_krw", BigDecimal.class)
        .addScalar("sku_code", String.class).addScalar("sku_name", String.class).addScalar("sku_status", String.class)
        .addScalar("product_name", String.class).addScalar("display_status", String.class)
        .addScalar("category_active", Boolean.class).addScalar("brand_active", Boolean.class).getResultList();
  }

  public List<SnapshotItem> findSnapshotItems(long snapshotId) {
    var item = new QSubscriptionReadRows_SnapshotItem("item");
    return queries.select(Projections.constructor(SnapshotItem.class, item.skuId, item.quantity))
        .from(item).where(item.snapshotId.eq(snapshotId)).orderBy(item.skuId.asc()).fetch();
  }

  public Optional<PendingChangeRow> lockPendingChange(long subscriptionId) {
    return Optional.ofNullable(queries.select(Projections.constructor(PendingChangeRow.class, pending.snapshotId, pending.targetScheduleId))
        .from(pending).where(pending.subscriptionId.eq(subscriptionId)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne());
  }

  public Optional<BillingMethodRow> lockBillingMethod(long memberId) {
    var billing = new QBillingPaymentMethodEntity("billing");
    return queries.select(Projections.constructor(BillingMethodRow.class, billing.id)).from(billing)
        .where(billing.memberId.eq(memberId), billing.status.eq("ACTIVE")).setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .fetch().stream().findFirst();
  }

  public Optional<ScheduleRow> lockSchedule(long scheduleId) {
    return Optional.ofNullable(queries.select(Projections.constructor(ScheduleRow.class,
            schedule.id, schedule.subscriptionId, schedule.scheduledDate, schedule.status, schedule.holdReason, schedule.effectiveSnapshotId))
        .from(schedule).where(schedule.id.eq(scheduleId)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne());
  }

  public Optional<SubscriptionRow> lockSubscription(long subscriptionId) {
    return Optional.ofNullable(queries.select(Projections.constructor(SubscriptionRow.class,
            subscription.id, subscription.memberId, subscription.status, subscription.runtimeManaged,
            subscription.version, subscription.currentSnapshotId.coalesce(0L), subscription.deliveryCycleWeeks))
        .from(subscription).where(subscription.id.eq(subscriptionId)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne());
  }

  public List<Candidate> findDueCandidates(
      LocalDate today, LocalDate repeatedToday, int batchSize) {
    var existing = new QSubscriptionOrderEntity("existing");
    var prior = new QSubscriptionScheduleEntity("prior");
    var context = new QSubscriptionOrderContextEntity("context");
    var payment = new QPaymentEntity("payment");
    var earlier = new QSubscriptionScheduleEntity("earlier");
    var earlierOrder = new QSubscriptionOrderEntity("earlierOrder");
    var unresolvedPrior = JPAExpressions.selectOne().from(prior).join(context).on(context.scheduleId.eq(prior.id))
        .join(payment).on(payment.orderId.eq(context.orderId))
        .where(prior.subscriptionId.eq(schedule.subscriptionId), precedes(prior, schedule), payment.status.ne("SUCCEEDED")).exists();
    var earlierUnordered = JPAExpressions.selectOne().from(earlier).leftJoin(earlierOrder).on(earlierOrder.scheduleId.eq(earlier.id))
        .where(earlier.subscriptionId.eq(schedule.subscriptionId), eligible(earlier), earlier.scheduledDate.loe(repeatedToday),
            earlierOrder.id.isNull(), precedes(earlier, schedule)).exists();
    return queries.select(Projections.constructor(Candidate.class, schedule.subscriptionId, schedule.id))
        .from(schedule).join(subscription).on(subscription.id.eq(schedule.subscriptionId))
        .leftJoin(existing).on(existing.scheduleId.eq(schedule.id))
        .where(subscription.runtimeManaged.isTrue(), subscription.status.eq("ACTIVE"), eligible(schedule),
            schedule.scheduledDate.loe(today), existing.id.isNull(), unresolvedPrior.not(), earlierUnordered.not())
        .orderBy(schedule.scheduledDate.asc(), schedule.id.asc()).limit(batchSize).fetch();
  }

  private BooleanExpression eligible(QSubscriptionScheduleEntity row) {
    return row.status.eq("SCHEDULED").or(row.status.eq("HELD").and(row.holdReason.eq("ORDER_STOCK_UNAVAILABLE")));
  }

  private BooleanExpression precedes(QSubscriptionScheduleEntity earlier, QSubscriptionScheduleEntity later) {
    return earlier.scheduledDate.lt(later.scheduledDate).or(earlier.scheduledDate.eq(later.scheduledDate).and(earlier.id.lt(later.id)));
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
