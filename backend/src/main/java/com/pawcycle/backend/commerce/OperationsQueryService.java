package com.pawcycle.backend.commerce;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Read projection only; it does not introduce an operations table. */
@Service
public class OperationsQueryService {
	private final JdbcTemplate jdbc; public OperationsQueryService(JdbcTemplate jdbc){this.jdbc=jdbc;}
	public List<Map<String,Object>> pending(){List<Map<String,Object>> rows=jdbc.queryForList("""
		SELECT 'RETURN_REQUESTED' AS type,id AS referenceId,requested_at AS createdAt FROM order_returns WHERE status='REQUESTED'
		UNION ALL SELECT 'REFUND_READY',id,requested_at FROM refunds WHERE status='READY'
		UNION ALL SELECT 'PAYMENT_UNKNOWN',id,created_at FROM payments WHERE status='UNKNOWN'
		UNION ALL SELECT 'PAYMENT_ACTION_REQUIRED',id,created_at FROM orders WHERE status='PAYMENT_ACTION_REQUIRED'
		UNION ALL SELECT 'REFUND_UNKNOWN',id,requested_at FROM refunds WHERE status='UNKNOWN'
		UNION ALL SELECT 'REFUND_FAILED',id,requested_at FROM refunds WHERE status='FAILED'
		UNION ALL SELECT 'DELIVERY_FAILED',id,failed_at FROM deliveries WHERE status='FAILED'
		UNION ALL SELECT 'MISSING_SHIPPING_ADDRESS',id,scheduled_date FROM subscription_schedules WHERE status='HELD' AND hold_reason='MISSING_SHIPPING_ADDRESS'
		UNION ALL SELECT 'MISSING_BILLING_METHOD',id,scheduled_date FROM subscription_schedules WHERE status='HELD' AND hold_reason='MISSING_BILLING_METHOD'
		UNION ALL SELECT 'PAYMENT_RETRY_EXHAUSTED',id,created_at FROM subscription_schedules WHERE status='HELD' AND hold_reason='PAYMENT_RETRY_EXHAUSTED'
		ORDER BY createdAt DESC""");
		for(Map<String,Object> row:rows){String type=(String)row.get("type");row.put("availableActions",switch(type){case "RETURN_REQUESTED"->List.of("APPROVE_RETURN","REJECT_RETURN");case "REFUND_READY"->List.of("PROCESS_REFUND");case "PAYMENT_UNKNOWN"->List.of("RECONCILE_PAYMENT");case "REFUND_UNKNOWN"->List.of("RECONCILE_REFUND");case "REFUND_FAILED"->List.of("RETRY_REFUND");case "DELIVERY_FAILED"->List.of("RESHIP_DELIVERY");default->List.of();});}return rows;}
}
