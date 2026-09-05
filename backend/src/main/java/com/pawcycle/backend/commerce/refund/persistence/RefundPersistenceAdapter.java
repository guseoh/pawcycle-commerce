package com.pawcycle.backend.commerce.refund.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class RefundPersistenceAdapter {
  private final JdbcTemplate queries;
  private final Clock clock;

  public RefundPersistenceAdapter(JdbcTemplate queries, Clock clock) {
    this.queries = queries;
    this.clock = clock;
  }

  public RefundWork findReadyForUpdate(long refundId) {
    return queries
        .query(
            "SELECT id,status,idempotency_key,amount FROM refunds WHERE id=? FOR UPDATE",
            (rs, rowNumber) ->
                new RefundWork(
                    rs.getLong("id"),
                    rs.getString("status"),
                    rs.getString("idempotency_key"),
                    rs.getBigDecimal("amount")),
            refundId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public void markProcessing(long refundId) {
    queries.update("UPDATE refunds SET status='PROCESSING',processed_at=? WHERE id=?", now(), refundId);
  }

  public RetryTarget findRetryTarget(long refundId) {
    return queries
        .query(
            "SELECT order_id AS orderId,source,source_id,cancellation_id AS cancellationId,return_id AS returnId,status,attempt_no AS attemptNo FROM refunds WHERE id=? FOR UPDATE",
            (rs, rowNumber) ->
                new RetryTarget(
                    rs.getLong("orderId"),
                    rs.getString("source"),
                    nullableLong(rs, "source_id"),
                    nullableLong(rs, "cancellationId"),
                    nullableLong(rs, "returnId"),
                    rs.getString("status"),
                    rs.getInt("attemptNo")),
            refundId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public RetryView findRetry(long sourceId, String source, long attemptNo) {
    return queries
        .query(
            "SELECT id AS refundId,status,attempt_no AS attemptNo FROM refunds WHERE source=? AND source_id=? AND attempt_no=? FOR UPDATE",
            (rs, rowNumber) ->
                new RetryView(rs.getLong("refundId"), rs.getString("status"), rs.getInt("attemptNo")),
            source,
            sourceId,
            attemptNo)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public RetryView createRetry(long refundId, int attempt) {
    queries.update(
        "INSERT INTO refunds(order_id,source,cancellation_id,return_id,status,amount,provider,idempotency_key,attempt_no,requested_at) SELECT order_id,source,cancellation_id,return_id,'READY',amount,'TOSS',?, ?,? FROM refunds WHERE id=?",
        "refund-" + UUID.randomUUID(),
        attempt,
        now(),
        refundId);
    long nextId = queries.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    return queries
        .query(
            "SELECT id AS refundId,status,attempt_no AS attemptNo FROM refunds WHERE id=?",
            (rs, rowNumber) -> new RetryView(rs.getLong("refundId"), rs.getString("status"), rs.getInt("attemptNo")),
            nextId)
        .getFirst();
  }

  public ReconciliationWork findForReconciliation(long refundId) {
    return queries
        .query(
            "SELECT id,status,idempotency_key,reconciliation_attempts AS reconciliationAttempts FROM refunds WHERE id=? FOR UPDATE",
            (rs, rowNumber) ->
                new ReconciliationWork(
                    rs.getLong("id"),
                    rs.getString("status"),
                    rs.getString("idempotency_key"),
                    rs.getInt("reconciliationAttempts")),
            refundId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public void incrementReconciliationAttempts(long refundId, int attempts) {
    queries.update(
        "UPDATE refunds SET reconciliation_attempts=?,last_reconciled_at=? WHERE id=?",
        attempts,
        now(),
        refundId);
  }

  public CompletionTarget findForCompletion(long refundId) {
    return queries
        .query(
            "SELECT refund.id,refund.order_id AS orderId,refund.source,refund.cancellation_id AS cancellationId,refund.return_id AS returnId,refund.status,orders.member_id AS memberId FROM refunds refund JOIN orders ON orders.id=refund.order_id WHERE refund.id=? FOR UPDATE",
            (rs, rowNumber) ->
                new CompletionTarget(
                    rs.getLong("id"),
                    rs.getLong("orderId"),
                    rs.getString("source"),
                    nullableLong(rs, "cancellationId"),
                    nullableLong(rs, "returnId"),
                    rs.getString("status"),
                    rs.getLong("memberId")),
            refundId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public void complete(long refundId, String status, String providerStatus, String failureCode) {
    queries.update(
        "UPDATE refunds SET status=?,provider_status=?,failure_code=?,completed_at=? WHERE id=?",
        status,
        providerStatus,
        failureCode,
        "SUCCEEDED".equals(status) ? now() : null,
        refundId);
  }

  public void releaseCoupon(long orderId) {
    queries.update(
        "UPDATE member_coupons SET status='AVAILABLE',reserved_order_id=NULL,used_at=NULL WHERE reserved_order_id=? AND status='USED'",
        orderId);
  }

  public void completeCancellation(long cancellationId) {
    queries.update("UPDATE order_cancellations SET status='COMPLETED',completed_at=? WHERE id=?", now(), cancellationId);
  }

  public void completeReturn(long returnId) {
    queries.update("UPDATE order_returns SET status='COMPLETED',completed_at=? WHERE id=?", now(), returnId);
  }

  public RefundView find(long refundId) {
    return queries
        .query(
            "SELECT id AS refundId,order_id AS orderId,source,status,amount,attempt_no AS attemptNo,reconciliation_attempts AS reconciliationAttempts,provider_status AS providerStatus,failure_code AS failureCode,requested_at AS requestedAt,processed_at AS processedAt,completed_at AS completedAt FROM refunds WHERE id=?",
            (rs, rowNumber) ->
                new RefundView(
                    rs.getLong("refundId"),
                    rs.getLong("orderId"),
                    rs.getString("source"),
                    rs.getString("status"),
                    rs.getBigDecimal("amount"),
                    rs.getInt("attemptNo"),
                    rs.getInt("reconciliationAttempts"),
                    rs.getString("providerStatus"),
                    rs.getString("failureCode"),
                    rs.getTimestamp("requestedAt"),
                    rs.getTimestamp("processedAt"),
                    rs.getTimestamp("completedAt")),
            refundId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  private Timestamp now() {
    return Timestamp.from(clock.instant());
  }

  private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : value;
  }

  public record RefundWork(long refundId, String status, String idempotencyKey, BigDecimal amount) {}
  public record RetryTarget(long orderId, String source, Long sourceId, Long cancellationId, Long returnId, String status, int attemptNo) {}
  public record RetryView(long refundId, String status, int attemptNo) {}
  public record ReconciliationWork(long refundId, String status, String idempotencyKey, int attempts) {}
  public record CompletionTarget(long refundId, long orderId, String source, Long cancellationId, Long returnId, String status, long memberId) {}
  public record RefundView(long refundId, long orderId, String source, String status, BigDecimal amount, int attemptNo, int reconciliationAttempts, String providerStatus, String failureCode, Timestamp requestedAt, Timestamp processedAt, Timestamp completedAt) {}
}
