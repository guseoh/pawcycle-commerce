package com.pawcycle.backend.commerce;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CancellationService {
	private final JdbcTemplate jdbc;
	private final TransactionTemplate tx;
	private final InventoryService inventory;

	public CancellationService(JdbcTemplate jdbc, org.springframework.transaction.PlatformTransactionManager manager, InventoryService inventory) {
		this.jdbc = jdbc;
		this.tx = new TransactionTemplate(manager);
		this.inventory = inventory;
	}

	public Map<String,Object> request(long memberId, long orderId, String reason) {
		return tx.execute(status -> {
			Map<String,Object> order = one("SELECT id,status FROM orders WHERE id=? AND member_id=? FOR UPDATE", orderId, memberId);
			if (order == null) throw new CommerceException(404,"ORDER_NOT_FOUND","요청한 리소스를 찾을 수 없습니다.");
			Map<String,Object> existing = one("SELECT cancellation.id AS cancellationId,cancellation.status,cancellation.reason,cancellation.requested_at AS requestedAt,cancellation.completed_at AS completedAt FROM order_cancellations cancellation WHERE cancellation.order_id=? FOR UPDATE", orderId);
			if (existing != null) return existing;
			Map<String,Object> delivery = one("SELECT id,status FROM deliveries WHERE order_id=? FOR UPDATE", orderId);
			Map<String,Object> payment = one("SELECT id FROM payments WHERE order_id=? AND status='SUCCEEDED' FOR UPDATE", orderId);
			if (!"PAID".equals(order.get("status")) || delivery == null || !"PREPARING".equals(delivery.get("status")) || payment == null) {
				throw new CommerceException(409,"CANCELLATION_NOT_ALLOWED","현재 주문은 취소할 수 없습니다.");
			}
			if (one("SELECT id FROM order_returns WHERE order_id=? FOR UPDATE", orderId) != null) {
				throw new CommerceException(409,"CANCELLATION_NOT_ALLOWED","반품이 진행 중인 주문은 취소할 수 없습니다.");
			}
			Timestamp now = Timestamp.from(Instant.now());
			jdbc.update("INSERT INTO order_cancellations(order_id,status,reason,requested_at) VALUES (?,'REFUND_PENDING',?,?)", orderId, reason, now);
			long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
			jdbc.update("UPDATE deliveries SET status='CANCELLED',cancelled_at=? WHERE id=?", now, delivery.get("id"));
			restore(orderId, id);
			jdbc.update("INSERT INTO refunds(order_id,source,cancellation_id,status,amount,provider,idempotency_key,attempt_no,requested_at) SELECT ?,'CANCELLATION',?,'READY',payment_amount,'TOSS',?,1,? FROM orders WHERE id=?", orderId, id, "refund-" + UUID.randomUUID(), now, orderId);
			return one("SELECT id AS cancellationId,status,reason,requested_at AS requestedAt,completed_at AS completedAt FROM order_cancellations WHERE id=?", id);
		});
	}

	private void restore(long orderId, long sourceId) {
		for (Map<String,Object> item : jdbc.queryForList("SELECT sku_id,quantity FROM order_items WHERE order_id=? ORDER BY sku_id", orderId)) {
			long sku = ((Number)item.get("sku_id")).longValue();
			int qty = ((Number)item.get("quantity")).intValue();
			inventory.restoreCancellation(sku, qty, sourceId);
		}
	}

	private Map<String,Object> one(String sql,Object... args) {
		var rows = jdbc.queryForList(sql,args);
		return rows.isEmpty() ? null : new LinkedHashMap<>(rows.getFirst());
	}
}
