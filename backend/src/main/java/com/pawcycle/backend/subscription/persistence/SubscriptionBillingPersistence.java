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
import org.springframework.stereotype.Component;

@Component
public class SubscriptionBillingPersistence {
  private final PaymentNativeLocks locks;
  private final PaymentPersistenceQueries writes;
  private final JPAQueryFactory queries;
  private final QSubscriptionScheduleEntity schedule = new QSubscriptionScheduleEntity("schedule");

  public SubscriptionBillingPersistence(EntityManager entities) {
    this.locks = new PaymentNativeLocks(entities);
    this.writes = new PaymentPersistenceQueries(entities);
    this.queries = new JPAQueryFactory(entities);
  }

  public int markUnknown(String providerStatus, long paymentId) {
    return writes.unknown(providerStatus, paymentId);
  }

  public int releaseMissingMethodHold(long scheduleId) {
    return writes.mutate(() -> queries.update(schedule).set(schedule.status, "SCHEDULED")
        .setNull(schedule.holdReason).where(schedule.id.eq(scheduleId), schedule.status.eq("HELD"),
            schedule.holdReason.eq("MISSING_BILLING_METHOD")).execute());
  }

  public int markOrderPaid(Timestamp now, long orderId) {
    return writes.orderPaid(now, orderId);
  }

  public int markSucceeded(String providerStatus, Timestamp now, long paymentId) {
    return writes.succeeded(providerStatus, now, paymentId);
  }

  public List<OrderItem> findOrderedItems(long orderId) {
    return writes.items(orderId, OrderItem.class, true);
  }

  public ProcessingPayment lockProcessingPayment(long paymentId) {
    return locks.first("""
        SELECT payment.id,payment.order_id,orders.member_id,context.schedule_id
        FROM payments payment
        JOIN orders orders ON orders.id=payment.order_id
        JOIN subscription_order_context context ON context.order_id=payment.order_id
        WHERE payment.id=? AND payment.status='PROCESSING' FOR UPDATE
        """, ProcessingPayment.class, paymentId);
  }

  public int markProcessing(long paymentId) {
    return writes.processing(paymentId);
  }

  public int holdMissingMethod(long scheduleId) {
    return writes.mutate(() -> queries.update(schedule).set(schedule.status, "HELD")
        .set(schedule.holdReason, "MISSING_BILLING_METHOD").where(schedule.id.eq(scheduleId)).execute());
  }

  public BillingWork lockWork(long paymentId) {
    return locks.first("""
        SELECT payment.id,payment.order_id,payment.provider_order_id,payment.amount,payment.status,
               method.billing_key,context.schedule_id,context.subscription_id,orders.member_id
        FROM payments payment
        JOIN orders orders ON orders.id=payment.order_id
        JOIN subscription_order_context context ON context.order_id=payment.order_id
        LEFT JOIN billing_payment_methods method ON method.member_id=orders.member_id AND method.status='ACTIVE'
        WHERE payment.id=? FOR UPDATE
        """, BillingWork.class, paymentId);
  }

  public List<BillingCandidate> findCandidates() {
    var payment = new QPaymentEntity("payment");
    return queries.select(Projections.constructor(BillingCandidate.class, payment.id, payment.status))
        .from(payment).where(payment.type.eq("BILLING"), payment.status.in("READY", "PROCESSING"))
        .orderBy(payment.id.asc()).fetch();
  }

  public record BillingCandidate(long id, String status) {}

  public record BillingWork(
      long id,
      long orderId,
      String providerOrderId,
      BigDecimal amount,
      String status,
      String billingKey,
      long scheduleId,
      long subscriptionId,
      long memberId) {}

  public record ProcessingPayment(long id, long orderId, long memberId, long scheduleId) {}

  public record OrderItem(long skuId, int quantity) {}
}
