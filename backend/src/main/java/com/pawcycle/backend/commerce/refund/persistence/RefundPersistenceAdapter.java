package com.pawcycle.backend.commerce.refund.persistence;

import jakarta.persistence.EntityManager;
import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.MutationQuery;
import org.hibernate.type.descriptor.java.JdbcTimestampJavaType;
import org.hibernate.type.internal.BasicTypeImpl;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class RefundPersistenceAdapter {
  private final EntityManager entities;
  private final Clock clock;

  public RefundPersistenceAdapter(EntityManager entities, Clock clock) {
    this.entities = entities;
    this.clock = clock;
  }

  public RefundWork findReadyForUpdate(long refundId) {
    return query("SELECT id,status,idempotency_key,amount FROM refunds WHERE id=?1 FOR UPDATE", RefundWork.class)
        .addScalar("id", Long.class)
        .addScalar("status", String.class)
        .addScalar("idempotency_key", String.class)
        .addScalar("amount", BigDecimal.class)
        .setParameter(1, refundId).getResultList()
        .stream()
        .findFirst()
        .orElse(null);
  }

  public void markProcessing(long refundId) {
    mutation("UPDATE refunds SET status='PROCESSING',processed_at=?1 WHERE id=?2")
        .setParameter(1, now(), timestampType())
        .setParameter(2, refundId).executeUpdate();
  }

  public RetryTarget findRetryTarget(long refundId) {
    return query("SELECT order_id AS orderId,source,source_id,cancellation_id AS cancellationId,return_id AS returnId,status,attempt_no AS attemptNo FROM refunds WHERE id=?1 FOR UPDATE", RetryTarget.class)
        .addScalar("orderId", Long.class)
        .addScalar("source", String.class)
        .addScalar("source_id", Long.class)
        .addScalar("cancellationId", Long.class)
        .addScalar("returnId", Long.class)
        .addScalar("status", String.class)
        .addScalar("attemptNo", Integer.class)
        .setParameter(1, refundId).getResultList()
        .stream()
        .findFirst()
        .orElse(null);
  }

  public RetryView findRetry(long sourceId, String source, long attemptNo) {
    return query("SELECT id AS refundId,status,attempt_no AS attemptNo FROM refunds WHERE source=?1 AND source_id=?2 AND attempt_no=?3 FOR UPDATE", RetryView.class)
        .addScalar("refundId", Long.class)
        .addScalar("status", String.class)
        .addScalar("attemptNo", Integer.class)
        .setParameter(1, source)
        .setParameter(2, sourceId)
        .setParameter(3, attemptNo).getResultList()
        .stream()
        .findFirst()
        .orElse(null);
  }

  public RetryView createRetry(long refundId, int attempt) {
    mutation("INSERT INTO refunds(order_id,source,cancellation_id,return_id,status,amount,provider,idempotency_key,attempt_no,requested_at) SELECT order_id,source,cancellation_id,return_id,'READY',amount,'TOSS',?1, ?2,?3 FROM refunds WHERE id=?4")
        .setParameter(1, "refund-" + UUID.randomUUID())
        .setParameter(2, attempt)
        .setParameter(3, now(), timestampType())
        .setParameter(4, refundId).executeUpdate();
    long nextId = query("SELECT LAST_INSERT_ID()", Long.class).getSingleResult();
    return query("SELECT id AS refundId,status,attempt_no AS attemptNo FROM refunds WHERE id=?1", RetryView.class)
        .addScalar("refundId", Long.class)
        .addScalar("status", String.class)
        .addScalar("attemptNo", Integer.class)
        .setParameter(1, nextId).getResultList()
        .getFirst();
  }

  public ReconciliationWork findForReconciliation(long refundId) {
    return query("SELECT id,status,idempotency_key,reconciliation_attempts AS reconciliationAttempts FROM refunds WHERE id=?1 FOR UPDATE", ReconciliationWork.class)
        .addScalar("id", Long.class)
        .addScalar("status", String.class)
        .addScalar("idempotency_key", String.class)
        .addScalar("reconciliationAttempts", Integer.class)
        .setParameter(1, refundId).getResultList()
        .stream()
        .findFirst()
        .orElse(null);
  }

  public void incrementReconciliationAttempts(long refundId, int attempts) {
    mutation("UPDATE refunds SET reconciliation_attempts=?1,last_reconciled_at=?2 WHERE id=?3")
        .setParameter(1, attempts)
        .setParameter(2, now(), timestampType())
        .setParameter(3, refundId).executeUpdate();
  }

  public CompletionTarget findForCompletion(long refundId) {
    return query("SELECT refund.id,refund.order_id AS orderId,refund.source,refund.cancellation_id AS cancellationId,refund.return_id AS returnId,refund.status,orders.member_id AS memberId FROM refunds refund JOIN orders ON orders.id=refund.order_id WHERE refund.id=?1 FOR UPDATE", CompletionTarget.class)
        .addScalar("id", Long.class)
        .addScalar("orderId", Long.class)
        .addScalar("source", String.class)
        .addScalar("cancellationId", Long.class)
        .addScalar("returnId", Long.class)
        .addScalar("status", String.class)
        .addScalar("memberId", Long.class)
        .setParameter(1, refundId).getResultList()
        .stream()
        .findFirst()
        .orElse(null);
  }

  public void complete(long refundId, String status, String providerStatus, String failureCode) {
    mutation("UPDATE refunds SET status=?1,provider_status=?2,failure_code=?3,completed_at=?4 WHERE id=?5")
        .setParameter(1, status, String.class)
        .setParameter(2, providerStatus, String.class)
        .setParameter(3, failureCode, String.class)
        .setParameter(4, "SUCCEEDED".equals(status) ? now() : null, timestampType())
        .setParameter(5, refundId).executeUpdate();
  }

  public void releaseCoupon(long orderId) {
    mutation("UPDATE member_coupons SET status='AVAILABLE',reserved_order_id=NULL,used_at=NULL WHERE reserved_order_id=?1 AND status='USED'")
        .setParameter(1, orderId).executeUpdate();
  }

  public void completeCancellation(long cancellationId) {
    mutation("UPDATE order_cancellations SET status='COMPLETED',completed_at=?1 WHERE id=?2")
        .setParameter(1, now(), timestampType())
        .setParameter(2, cancellationId).executeUpdate();
  }

  public void completeReturn(long returnId) {
    mutation("UPDATE order_returns SET status='COMPLETED',completed_at=?1 WHERE id=?2")
        .setParameter(1, now(), timestampType())
        .setParameter(2, returnId).executeUpdate();
  }

  public RefundView find(long refundId) {
    return query("SELECT id AS refundId,order_id AS orderId,source,status,amount,attempt_no AS attemptNo,reconciliation_attempts AS reconciliationAttempts,provider_status AS providerStatus,failure_code AS failureCode,requested_at AS requestedAt,processed_at AS processedAt,completed_at AS completedAt FROM refunds WHERE id=?1", RefundView.class)
        .addScalar("refundId", Long.class)
        .addScalar("orderId", Long.class)
        .addScalar("source", String.class)
        .addScalar("status", String.class)
        .addScalar("amount", BigDecimal.class)
        .addScalar("attemptNo", Integer.class)
        .addScalar("reconciliationAttempts", Integer.class)
        .addScalar("providerStatus", String.class)
        .addScalar("failureCode", String.class)
        .addScalar("requestedAt", timestampType())
        .addScalar("processedAt", timestampType())
        .addScalar("completedAt", timestampType())
        .setParameter(1, refundId).getResultList()
        .stream()
        .findFirst()
        .orElse(null);
  }

  private <T> NativeQuery<T> query(String sql, Class<T> type) {
    // Preserve JDBC statement order and visibility of explicitly flushed state.
    return entities.unwrap(Session.class).createNativeQuery(sql, type)
        .setHibernateFlushMode(FlushMode.COMMIT);
  }

  private MutationQuery mutation(String sql) {
    return entities.unwrap(Session.class).createNativeMutationQuery(sql)
        .setHibernateFlushMode(FlushMode.COMMIT);
  }

  private static BasicTypeImpl<Timestamp> timestampType() {
    return new BasicTypeImpl<>(JdbcTimestampJavaType.INSTANCE, new RefundTimestampJdbcType());
  }

  private Timestamp now() {
    return Timestamp.from(clock.instant());
  }


  public record RefundWork(long refundId, String status, String idempotencyKey, BigDecimal amount) {}
  public record RetryTarget(long orderId, String source, Long sourceId, Long cancellationId, Long returnId, String status, int attemptNo) {}
  public record RetryView(long refundId, String status, int attemptNo) {}
  public record ReconciliationWork(long refundId, String status, String idempotencyKey, int attempts) {}
  public record CompletionTarget(long refundId, long orderId, String source, Long cancellationId, Long returnId, String status, long memberId) {}
  public record RefundView(long refundId, long orderId, String source, String status, BigDecimal amount, int attemptNo, int reconciliationAttempts, String providerStatus, String failureCode, Timestamp requestedAt, Timestamp processedAt, Timestamp completedAt) {}
}
