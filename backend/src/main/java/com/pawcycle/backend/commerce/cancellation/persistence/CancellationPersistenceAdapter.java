package com.pawcycle.backend.commerce.cancellation.persistence;

import jakarta.persistence.EntityManager;
import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.MutationQuery;
import org.hibernate.type.descriptor.java.JdbcTimestampJavaType;
import org.hibernate.type.internal.BasicTypeImpl;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class CancellationPersistenceAdapter {
  private final EntityManager entities;
  private final Clock clock;

  public CancellationPersistenceAdapter(EntityManager entities, Clock clock) {
    this.entities = entities;
    this.clock = clock;
  }

  public OrderLock findOrderForUpdate(long memberId, long orderId) {
    return query("SELECT id,status FROM orders WHERE id=?1 AND member_id=?2 FOR UPDATE", OrderLock.class)
        .addScalar("id", Long.class)
        .addScalar("status", String.class)
        .setParameter(1, orderId)
        .setParameter(2, memberId).getResultList()
        .stream()
        .findFirst()
        .orElse(null);
  }

  public CancellationView findExisting(long orderId) {
    return query("SELECT id AS cancellationId,status,reason,requested_at AS requestedAt,completed_at AS completedAt FROM order_cancellations WHERE order_id=?1 FOR UPDATE", CancellationView.class)
        .addScalar("cancellationId", Long.class)
        .addScalar("status", String.class)
        .addScalar("reason", String.class)
        .addScalar("requestedAt", timestampType())
        .addScalar("completedAt", timestampType())
        .setParameter(1, orderId).getResultList()
        .stream()
        .findFirst()
        .orElse(null);
  }

  public DeliveryLock findDeliveryForUpdate(long orderId) {
    return query("SELECT id,status FROM deliveries WHERE order_id=?1 FOR UPDATE", DeliveryLock.class)
        .addScalar("id", Long.class)
        .addScalar("status", String.class)
        .setParameter(1, orderId).getResultList()
        .stream()
        .findFirst()
        .orElse(null);
  }

  public boolean hasReturnForUpdate(long orderId) {
    return !query("SELECT id FROM order_returns WHERE order_id=?1 FOR UPDATE", Long.class)
        .setParameter(1, orderId).getResultList()
        .isEmpty();
  }

  public boolean hasSuccessfulPayment(long orderId) {
    return !query("SELECT id FROM payments WHERE order_id=?1 AND status='SUCCEEDED' FOR UPDATE", Long.class)
        .setParameter(1, orderId).getResultList()
        .isEmpty();
  }

  public long create(long orderId, String reason) {
    Timestamp now = now();
    mutation("INSERT INTO order_cancellations(order_id,status,reason,requested_at) VALUES (?1,'REFUND_PENDING',?2,?3)")
        .setParameter(1, orderId)
        .setParameter(2, reason, String.class)
        .setParameter(3, now, timestampType()).executeUpdate();
    return query("SELECT LAST_INSERT_ID()", Long.class).getSingleResult();
  }

  public void cancelDelivery(long deliveryId) {
    mutation("UPDATE deliveries SET status='CANCELLED',cancelled_at=?1 WHERE id=?2")
        .setParameter(1, now(), timestampType())
        .setParameter(2, deliveryId).executeUpdate();
  }

  public void createRefund(long orderId, long cancellationId) {
    mutation("INSERT INTO refunds(order_id,source,cancellation_id,status,amount,provider,idempotency_key,attempt_no,requested_at) SELECT ?1,'CANCELLATION',?2,'READY',payment_amount,'TOSS',?3,1,?4 FROM orders WHERE id=?5")
        .setParameter(1, orderId)
        .setParameter(2, cancellationId)
        .setParameter(3, "refund-" + UUID.randomUUID())
        .setParameter(4, now(), timestampType())
        .setParameter(5, orderId).executeUpdate();
  }

  public CancellationView find(long cancellationId) {
    return query("SELECT id AS cancellationId,status,reason,requested_at AS requestedAt,completed_at AS completedAt FROM order_cancellations WHERE id=?1", CancellationView.class)
        .addScalar("cancellationId", Long.class)
        .addScalar("status", String.class)
        .addScalar("reason", String.class)
        .addScalar("requestedAt", timestampType())
        .addScalar("completedAt", timestampType())
        .setParameter(1, cancellationId).getResultList()
        .stream()
        .findFirst()
        .orElse(null);
  }

  public List<OrderItem> findOrderItems(long orderId) {
    return query("SELECT sku_id AS skuId,quantity FROM order_items WHERE order_id=?1 ORDER BY sku_id", OrderItem.class)
        .addScalar("skuId", Long.class)
        .addScalar("quantity", Integer.class)
        .setParameter(1, orderId).getResultList();
  }

  private <T> NativeQuery<T> query(String sql, Class<T> type) {
    // JDBC did not auto-flush pending managed state before these ordered SQL statements.
    return entities.unwrap(Session.class).createNativeQuery(sql, type)
        .setHibernateFlushMode(FlushMode.COMMIT);
  }

  private MutationQuery mutation(String sql) {
    return entities.unwrap(Session.class).createNativeMutationQuery(sql)
        .setHibernateFlushMode(FlushMode.COMMIT);
  }

  private static final BasicTypeImpl<Timestamp> LEGACY_TIMESTAMP_TYPE =
      new BasicTypeImpl<>(JdbcTimestampJavaType.INSTANCE, new CancellationTimestampJdbcType());

  private static BasicTypeImpl<Timestamp> timestampType() {
    return LEGACY_TIMESTAMP_TYPE;
  }

  private Timestamp now() {
    return Timestamp.from(clock.instant());
  }

  public record OrderLock(long orderId, String status) {}
  public record DeliveryLock(long deliveryId, String status) {}
  public record OrderItem(long skuId, int quantity) {}
  public record CancellationView(long cancellationId, String status, String reason, Timestamp requestedAt, Timestamp completedAt) {}
}
