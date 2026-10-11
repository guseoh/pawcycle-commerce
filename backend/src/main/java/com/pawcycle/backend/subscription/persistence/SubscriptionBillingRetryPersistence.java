package com.pawcycle.backend.subscription.persistence;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import com.pawcycle.backend.commerce.payment.domain.QPaymentEntity;
import com.pawcycle.backend.commerce.payment.persistence.PaymentNativeLocks;
import com.pawcycle.backend.commerce.payment.persistence.PaymentPersistenceQueries;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import com.pawcycle.backend.commerce.payment.domain.PaymentEntity;
import com.pawcycle.backend.commerce.payment.persistence.PaymentRepository;
import com.pawcycle.backend.commerce.order.domain.QCommerceOrderEntity;
import com.querydsl.jpa.JPAExpressions;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionBillingRetryPersistence {
  private final EntityManager entities;
  private final PaymentRepository payments;
  private final PaymentNativeLocks locks;
  private final PaymentPersistenceQueries writes;
  private final JPAQueryFactory queries;
  private final QPaymentEntity payment = new QPaymentEntity("payment");
  private final QSubscriptionScheduleEntity schedule = new QSubscriptionScheduleEntity("schedule");

  public SubscriptionBillingRetryPersistence(EntityManager entities, PaymentRepository payments) {
    this.entities = entities;
    this.payments = payments;
    this.locks = new PaymentNativeLocks(entities);
    this.writes = new PaymentPersistenceQueries(entities);
    this.queries = new JPAQueryFactory(entities);
  }

  public InventoryState lockInventory(long skuId) {
    return locks.first("""
        SELECT available_quantity,reserved_quantity,version FROM inventories WHERE sku_id=? FOR UPDATE
        """, InventoryState.class, skuId);
  }

  public List<OrderItem> findOrderedItems(long orderId) {
    return writes.items(orderId, OrderItem.class, true);
  }

  public List<OrderItem> findItems(long orderId) {
    return writes.items(orderId, OrderItem.class, false);
  }

  public int markPaymentOrderActionRequired(long paymentId) {
    var order = new QCommerceOrderEntity("orders");
    return writes.mutate(() -> queries.update(order).set(order.status, "PAYMENT_ACTION_REQUIRED")
        .where(order.id.eq(JPAExpressions.select(payment.orderId).from(payment).where(payment.id.eq(paymentId)))).execute());
  }

  public int recordReconciliation(int attempts, Timestamp now, long paymentId) {
    return writes.reconciliation(attempts, now, paymentId);
  }

  public ReconciliationRow lockReconciliation(long paymentId) {
    return locks.first("""
        SELECT reconciliation_attempts,status FROM payments WHERE id=? FOR UPDATE
        """, ReconciliationRow.class, paymentId);
  }

  public int releaseStockHold(long scheduleId) {
    return writes.mutate(() -> queries.update(schedule).set(schedule.status, "SCHEDULED")
        .setNull(schedule.holdReason).where(schedule.id.eq(scheduleId), schedule.status.eq("HELD"),
            schedule.holdReason.eq("PAYMENT_RETRY_STOCK_UNAVAILABLE")).execute());
  }

  public long insertAttempt(
      long orderId,
      BigDecimal amount,
      String providerOrderId,
      String idempotencyKey,
      int attempt,
      Timestamp requestedAt,
      Timestamp createdAt) {
    var row = PaymentEntity.billing(orderId, amount, providerOrderId, idempotencyKey, attempt,
        PaymentPersistenceQueries.jdbcTime(requestedAt), PaymentPersistenceQueries.jdbcTime(createdAt));
    payments.saveAndFlush(row);
    long id = row.getId();
    entities.detach(row);
    return id;
  }

  public int holdStockUnavailable(long scheduleId) {
    return writes.mutate(() -> queries.update(schedule).set(schedule.status, "HELD")
        .set(schedule.holdReason, "PAYMENT_RETRY_STOCK_UNAVAILABLE").where(schedule.id.eq(scheduleId)).execute());
  }

  public RetryOrder lockOrder(long orderId) {
    return locks.first("""
        SELECT payment_amount,status FROM orders WHERE id=? FOR UPDATE
        """, RetryOrder.class, orderId);
  }

  public Long findExistingAttempt(long orderId, int attempt) {
    return queries.select(payment.id).from(payment)
        .where(payment.orderId.eq(orderId), payment.attemptNo.eq(attempt)).fetchOne();
  }

  public Integer countStockHolds(long scheduleId) {
    return Math.toIntExact(queries.select(schedule.count()).from(schedule)
        .where(schedule.id.eq(scheduleId), schedule.status.eq("HELD"),
            schedule.holdReason.eq("PAYMENT_RETRY_STOCK_UNAVAILABLE")).fetchOne());
  }

  public RetryAttempt lockRetryAttempt(long paymentId) {
    return locks.first("""
        SELECT payment.order_id,payment.attempt_no,payment.status,context.schedule_id
        FROM payments payment JOIN subscription_order_context context ON context.order_id=payment.order_id
        WHERE payment.id=? FOR UPDATE
        """, RetryAttempt.class, paymentId);
  }

  public int holdRetryExhausted(long scheduleId) {
    return writes.mutate(() -> queries.update(schedule).set(schedule.status, "HELD")
        .set(schedule.holdReason, "PAYMENT_RETRY_EXHAUSTED").where(schedule.id.eq(scheduleId)).execute());
  }

  public int markOrderActionRequired(long orderId) {
    return writes.orderStatus("PAYMENT_ACTION_REQUIRED", orderId);
  }

  public int markFailed(String providerStatus, String failureCode, Timestamp now, long paymentId) {
    return writes.failed(providerStatus, failureCode, now, paymentId);
  }

  public FailureAttempt lockFailureAttempt(long paymentId) {
    return locks.first("""
        SELECT payment.id,payment.order_id,payment.attempt_no,payment.status,context.schedule_id
        FROM payments payment JOIN subscription_order_context context ON context.order_id=payment.order_id
        WHERE payment.id=? FOR UPDATE
        """, FailureAttempt.class, paymentId);
  }

  public record FailureAttempt(
      long id, long orderId, int attemptNo, String status, long scheduleId) {}

  public record RetryAttempt(long orderId, int attemptNo, String status, long scheduleId) {}

  public record RetryOrder(BigDecimal amount, String status) {}

  public record ReconciliationRow(int attempts, String status) {}

  public record OrderItem(long skuId, int quantity) {}

  public record InventoryState(int available, int reserved, long version) {}
}
