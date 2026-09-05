package com.pawcycle.backend.commerce.operations.persistence;

import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OperationsQueryRepository {
  private final JdbcTemplate jdbc;

  public OperationsQueryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<PendingRow> findPending() {
    return jdbc.queryForList(
        """
            SELECT 'DELIVERY_PREPARING' AS type,id AS referenceId,COALESCE(shipped_at,delivered_at,failed_at,CURRENT_TIMESTAMP(6)) AS createdAt,NULL AS attemptNo FROM deliveries WHERE status='PREPARING'
            UNION ALL SELECT 'DELIVERY_SHIPPED',id,shipped_at,NULL FROM deliveries WHERE status='SHIPPED'
            UNION ALL SELECT 'DELIVERY_FAILED',id,failed_at,NULL FROM deliveries WHERE status='FAILED'
            UNION ALL SELECT 'RETURN_REQUESTED',id,requested_at,NULL FROM order_returns WHERE status='REQUESTED'
            UNION ALL SELECT 'RETURN_APPROVED',id,decided_at,NULL FROM order_returns WHERE status='APPROVED'
            UNION ALL SELECT 'REFUND_READY',id,requested_at,attempt_no FROM refunds WHERE status='READY'
            UNION ALL SELECT 'REFUND_PROCESSING',id,processed_at,attempt_no FROM refunds WHERE status='PROCESSING' AND reconciliation_attempts<10
            UNION ALL SELECT 'PAYMENT_UNKNOWN',id,created_at,NULL FROM payments WHERE status='UNKNOWN' AND reconciliation_attempts<10
            UNION ALL SELECT 'PAYMENT_ACTION_REQUIRED',id,created_at,NULL FROM orders WHERE status='PAYMENT_ACTION_REQUIRED'
            UNION ALL SELECT 'REFUND_UNKNOWN',id,requested_at,attempt_no FROM refunds WHERE status='UNKNOWN' AND reconciliation_attempts<10
            UNION ALL SELECT 'REFUND_FAILED',failed.id,failed.requested_at,failed.attempt_no
              FROM refunds failed
             WHERE failed.status='FAILED'
               AND NOT EXISTS (
                   SELECT 1 FROM refunds newer
                   WHERE newer.source=failed.source
                     AND newer.source_id=failed.source_id
                     AND newer.attempt_no>failed.attempt_no
               )
            UNION ALL SELECT 'PAYMENT_PROCESSING',id,created_at,NULL FROM payments WHERE type='BILLING' AND status='PROCESSING' AND reconciliation_attempts<10
            UNION ALL SELECT 'PAYMENT_RETRY_STOCK_UNAVAILABLE',payment.id,schedule.scheduled_date,payment.attempt_no
              FROM subscription_schedules schedule
              JOIN subscription_order_context context ON context.schedule_id=schedule.id
              JOIN payments payment ON payment.order_id=context.order_id AND payment.status='FAILED' AND payment.attempt_no=(SELECT MAX(latest.attempt_no) FROM payments latest WHERE latest.order_id=payment.order_id)
             WHERE schedule.status='HELD' AND schedule.hold_reason='PAYMENT_RETRY_STOCK_UNAVAILABLE'
            UNION ALL SELECT 'MISSING_SHIPPING_ADDRESS',id,scheduled_date,NULL FROM subscription_schedules WHERE status='HELD' AND hold_reason='MISSING_SHIPPING_ADDRESS'
            UNION ALL SELECT 'MISSING_BILLING_METHOD',id,scheduled_date,NULL FROM subscription_schedules WHERE status='HELD' AND hold_reason='MISSING_BILLING_METHOD'
            UNION ALL SELECT 'PAYMENT_RETRY_EXHAUSTED',id,scheduled_date,NULL FROM subscription_schedules WHERE status='HELD' AND hold_reason='PAYMENT_RETRY_EXHAUSTED'
            ORDER BY createdAt DESC
            """)
        .stream()
        .map(
            row ->
                new PendingRow(
                    (String) row.get("type"),
                    ((Number) row.get("referenceId")).longValue(),
                    (Timestamp) row.get("createdAt"),
                    row.get("attemptNo") == null
                        ? null
                        : ((Number) row.get("attemptNo")).intValue()))
        .toList();
  }

  public record PendingRow(String type, long referenceId, Timestamp createdAt, Integer attemptNo) {}
}
