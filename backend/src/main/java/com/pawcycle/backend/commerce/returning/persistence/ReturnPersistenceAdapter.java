package com.pawcycle.backend.commerce.returning.persistence;

import jakarta.persistence.EntityManager;
import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.MutationQuery;
import org.hibernate.type.descriptor.java.JdbcTimestampJavaType;
import org.hibernate.type.internal.BasicTypeImpl;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class ReturnPersistenceAdapter {
  private final EntityManager entities;
  private final Clock clock;

  public ReturnPersistenceAdapter(EntityManager entities, Clock clock) {
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

  public ReturnView findByOrderForUpdate(long orderId) {
    return query("SELECT id AS returnId,status,reason,rejection_reason AS rejectionReason,restock,requested_at AS requestedAt,decided_at AS decidedAt,received_at AS receivedAt,completed_at AS completedAt FROM order_returns WHERE order_id=?1 FOR UPDATE", ReturnView.class)
        .addScalar("returnId", Long.class)
        .addScalar("status", String.class)
        .addScalar("reason", String.class)
        .addScalar("rejectionReason", String.class)
        .addScalar("restock", Boolean.class)
        .addScalar("requestedAt", timestampType())
        .addScalar("decidedAt", timestampType())
        .addScalar("receivedAt", timestampType())
        .addScalar("completedAt", timestampType())
        .setParameter(1, orderId).getResultList()
        .stream()
        .findFirst()
        .orElse(null);
  }

  public boolean hasCancellationForUpdate(long orderId) {
    return !query("SELECT id FROM order_cancellations WHERE order_id=?1 FOR UPDATE", Long.class)
        .setParameter(1, orderId).getResultList()
        .isEmpty();
  }

  public DeliveryView findDeliveryForUpdate(long orderId) {
    return query("SELECT status,delivered_at AS deliveredAt FROM deliveries WHERE order_id=?1 FOR UPDATE", DeliveryView.class)
        .addScalar("status", String.class)
        .addScalar("deliveredAt", timestampType())
        .setParameter(1, orderId).getResultList()
        .stream()
        .findFirst()
        .orElse(null);
  }

  public long create(long orderId, String reason) {
    mutation("INSERT INTO order_returns(order_id,status,reason,requested_at) VALUES (?1,'REQUESTED',?2,?3)")
        .setParameter(1, orderId)
        .setParameter(2, reason, String.class)
        .setParameter(3, now(), timestampType()).executeUpdate();
    return query("SELECT LAST_INSERT_ID()", Long.class).getSingleResult();
  }

  public ReturnView find(long returnId) {
    return query("SELECT id AS returnId,status,reason,rejection_reason AS rejectionReason,restock,requested_at AS requestedAt,decided_at AS decidedAt,received_at AS receivedAt,completed_at AS completedAt FROM order_returns WHERE id=?1", ReturnView.class)
        .addScalar("returnId", Long.class)
        .addScalar("status", String.class)
        .addScalar("reason", String.class)
        .addScalar("rejectionReason", String.class)
        .addScalar("restock", Boolean.class)
        .addScalar("requestedAt", timestampType())
        .addScalar("decidedAt", timestampType())
        .addScalar("receivedAt", timestampType())
        .addScalar("completedAt", timestampType())
        .setParameter(1, returnId).getResultList()
        .stream()
        .findFirst()
        .orElse(null);
  }

  public ReturnDecision findForDecision(long returnId) {
    return query("SELECT return_order.id AS returnId,return_order.order_id AS orderId,return_order.status,orders.member_id AS memberId FROM order_returns return_order JOIN orders ON orders.id=return_order.order_id WHERE return_order.id=?1 FOR UPDATE", ReturnDecision.class)
        .addScalar("returnId", Long.class)
        .addScalar("orderId", Long.class)
        .addScalar("status", String.class)
        .addScalar("memberId", Long.class)
        .setParameter(1, returnId).getResultList()
        .stream()
        .findFirst()
        .orElse(null);
  }

  public void decide(long returnId, String state, String reason, long adminId) {
    mutation("UPDATE order_returns SET status=?1,rejection_reason=?2,decided_at=?3,decided_by_admin_id=?4 WHERE id=?5")
        .setParameter(1, state, String.class)
        .setParameter(2, reason, String.class)
        .setParameter(3, now(), timestampType())
        .setParameter(4, adminId)
        .setParameter(5, returnId).executeUpdate();
  }

  public ReceiveTarget findForReceive(long returnId) {
    return query("SELECT id,order_id AS orderId,status FROM order_returns WHERE id=?1 FOR UPDATE", ReceiveTarget.class)
        .addScalar("id", Long.class)
        .addScalar("orderId", Long.class)
        .addScalar("status", String.class)
        .setParameter(1, returnId).getResultList()
        .stream()
        .findFirst()
        .orElse(null);
  }

  public void receive(long returnId, boolean restock, long adminId) {
    mutation("UPDATE order_returns SET status='REFUND_PENDING',restock=?1,received_at=?2,received_by_admin_id=?3 WHERE id=?4")
        .setParameter(1, restock)
        .setParameter(2, now(), timestampType())
        .setParameter(3, adminId)
        .setParameter(4, returnId).executeUpdate();
  }

  public void createRefund(long orderId, long returnId) {
    mutation("INSERT INTO refunds(order_id,source,return_id,status,amount,provider,idempotency_key,attempt_no,requested_at) SELECT ?1,'RETURN',?2,'READY',payment_amount,'TOSS',?3,1,?4 FROM orders WHERE id=?5")
        .setParameter(1, orderId)
        .setParameter(2, returnId)
        .setParameter(3, "refund-" + UUID.randomUUID())
        .setParameter(4, now(), timestampType())
        .setParameter(5, orderId).executeUpdate();
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
      new BasicTypeImpl<>(JdbcTimestampJavaType.INSTANCE, new ReturnTimestampJdbcType());

  private static BasicTypeImpl<Timestamp> timestampType() {
    return LEGACY_TIMESTAMP_TYPE;
  }

  private Timestamp now() {
    return Timestamp.from(clock.instant());
  }


  public record OrderLock(long orderId, String status) {}
  public record DeliveryView(String status, Timestamp deliveredAt) {}
  public record ReturnDecision(long returnId, long orderId, String status, long memberId) {}
  public record ReceiveTarget(long returnId, long orderId, String status) {}
  public record OrderItem(long skuId, int quantity) {}
  public record ReturnView(long returnId, String status, String reason, String rejectionReason, Boolean restock, Timestamp requestedAt, Timestamp decidedAt, Timestamp receivedAt, Timestamp completedAt) {}
}
