package com.pawcycle.backend.commerce.payment.persistence;

import com.pawcycle.backend.commerce.order.domain.QCommerceOrderEntity;
import com.pawcycle.backend.commerce.order.domain.QCommerceOrderItemEntity;
import com.pawcycle.backend.commerce.payment.domain.QPaymentEntity;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.LongSupplier;

/** Shared typed projections and field-specific writes, without entity transition side effects. */
public final class PaymentPersistenceQueries {
  private final EntityManager entities;
  private final JPAQueryFactory queries;
  private final QPaymentEntity payment = new QPaymentEntity("payment");
  private final QCommerceOrderEntity order = new QCommerceOrderEntity("orders");
  private final QCommerceOrderItemEntity item = new QCommerceOrderItemEntity("item");

  public PaymentPersistenceQueries(EntityManager entities) {
    this.entities = entities;
    this.queries = new JPAQueryFactory(entities);
  }

  public <T> List<T> items(long orderId, Class<T> result, boolean ordered) {
    var query = queries.select(Projections.constructor(result, item.skuId, item.quantity))
        .from(item).where(item.orderId.eq(orderId));
    if (ordered) query.orderBy(item.skuId.asc());
    return query.fetch();
  }

  public int processing(long id) {
    return mutate(() -> queries.update(payment).set(payment.status, "PROCESSING").where(payment.id.eq(id)).execute());
  }

  public int unknown(String providerStatus, long id) {
    return mutate(() -> queries.update(payment).set(payment.status, "UNKNOWN").set(payment.providerStatus, providerStatus)
        .set(payment.failureCode, "PROVIDER_RESULT_UNKNOWN").where(payment.id.eq(id), payment.status.eq("PROCESSING")).execute());
  }

  public int succeeded(String providerStatus, Timestamp now, long id) {
    return mutate(() -> queries.update(payment).set(payment.status, "SUCCEEDED").set(payment.providerStatus, providerStatus)
        .set(payment.approvedAt, jdbcTime(now)).where(payment.id.eq(id)).execute());
  }

  public int failed(String providerStatus, String code, Timestamp now, long id) {
    return mutate(() -> queries.update(payment).set(payment.status, "FAILED").set(payment.providerStatus, providerStatus)
        .set(payment.failureCode, code).set(payment.failedAt, jdbcTime(now)).where(payment.id.eq(id)).execute());
  }

  public int reconciliation(int attempts, Timestamp now, long id) {
    return mutate(() -> queries.update(payment).set(payment.reconciliationAttempts, attempts)
        .set(payment.lastReconciledAt, jdbcTime(now)).where(payment.id.eq(id)).execute());
  }

  public int orderPaid(Timestamp now, long id) {
    return mutate(() -> queries.update(order).set(order.status, "PAID").set(order.paidAt, jdbcTime(now)).where(order.id.eq(id)).execute());
  }

  public int orderStatus(String status, long id) {
    return mutate(() -> queries.update(order).set(order.status, status).where(order.id.eq(id)).execute());
  }

  /** Flush pending work before bulk writes, then invalidate managed records for read-after-write. */
  public int mutate(LongSupplier update) {
    entities.flush();
    int count = Math.toIntExact(update.getAsLong());
    entities.clear();
    return count;
  }

  /** Preserve the old no-Calendar Timestamp wall clock with existing UTC-Calendar mappings. */
  public static LocalDateTime jdbcTime(Timestamp value) {
    if (value == null) return null;
    return LocalDateTime.ofInstant(value.toLocalDateTime().toInstant(ZoneOffset.UTC), ZoneId.systemDefault());
  }
}
