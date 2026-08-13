package com.pawcycle.backend.commerce;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Read projection only; it does not introduce an operations table. */
@Service
public class OperationsQueryService {
	private final JdbcTemplate jdbc;
	public OperationsQueryService(JdbcTemplate jdbc){this.jdbc=jdbc;}

	public List<Map<String,Object>> pending() {
		List<Map<String,Object>> rows=jdbc.queryForList("""
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
		ORDER BY createdAt DESC""");
		for(Map<String,Object> row:rows) {
			String type=(String)row.get("type");
			row.put("availableActions",switch(type) {
				case "DELIVERY_PREPARING" -> List.of("SHIP_DELIVERY");
				case "DELIVERY_SHIPPED" -> List.of("COMPLETE_DELIVERY","FAIL_DELIVERY");
				case "DELIVERY_FAILED" -> List.of("RESHIP_DELIVERY");
				case "RETURN_REQUESTED" -> List.of("APPROVE_RETURN","REJECT_RETURN");
				case "RETURN_APPROVED" -> List.of("RECEIVE_RETURN");
				case "REFUND_READY" -> List.of("PROCESS_REFUND");
				case "REFUND_PROCESSING" -> List.of("RECONCILE_REFUND");
				case "PAYMENT_UNKNOWN" -> List.of("RECONCILE_PAYMENT");
				case "REFUND_UNKNOWN" -> List.of("RECONCILE_REFUND");
				case "REFUND_FAILED" -> ((Number)row.get("attemptNo")).intValue() < 3 ? List.of("RETRY_REFUND") : List.of();
				case "PAYMENT_PROCESSING" -> List.of("RECONCILE_PAYMENT");
				case "PAYMENT_RETRY_STOCK_UNAVAILABLE" -> List.of("RETRY_BILLING");
				default -> List.of();
			});
		}
		return rows;
	}
}
