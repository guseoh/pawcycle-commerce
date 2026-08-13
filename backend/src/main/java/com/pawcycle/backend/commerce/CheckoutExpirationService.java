package com.pawcycle.backend.commerce;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CheckoutExpirationService {
	private final JdbcTemplate jdbc;
	private final TransactionTemplate transaction;

	public CheckoutExpirationService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
		this.jdbc = jdbc;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	public int expireDue(int batchSize) {
		if (batchSize < 1) throw new IllegalArgumentException("batchSize must be positive");
		List<Long> paymentIds = jdbc.queryForList("""
			SELECT id FROM payments
			WHERE type='NORMAL' AND status='READY' AND expires_at IS NOT NULL AND expires_at<=?
			ORDER BY expires_at,id LIMIT ?""", Long.class, now(), batchSize);
		int expired = 0;
		for (Long paymentId : paymentIds) {
			if (Boolean.TRUE.equals(transaction.execute(status -> expireOne(paymentId)))) expired++;
		}
		return expired;
	}

	private boolean expireOne(long paymentId) {
		Map<String,Object> payment = jdbc.query("""
			SELECT payment.id,payment.order_id,payment.status,orders.status AS order_status
			FROM payments payment JOIN orders ON orders.id=payment.order_id
			WHERE payment.id=? FOR UPDATE""",
				rs -> rs.next() ? Map.of(
					"orderId", rs.getLong("order_id"),
					"status", rs.getString("status"),
					"orderStatus", rs.getString("order_status")) : null,
				paymentId);
		if (payment == null
				|| !"READY".equals(payment.get("status"))
				|| !"PAYMENT_PENDING".equals(payment.get("orderStatus"))) {
			return false;
		}
		long orderId = (Long) payment.get("orderId");
		for (Map<String,Object> item : jdbc.queryForList("SELECT sku_id,quantity FROM order_items WHERE order_id=?", orderId)) {
			long skuId = ((Number)item.get("sku_id")).longValue();
			int quantity = ((Number)item.get("quantity")).intValue();
			Map<String,Object> inventory = jdbc.query("SELECT available_quantity,reserved_quantity FROM inventories WHERE sku_id=? FOR UPDATE",
					rs -> rs.next() ? Map.of("available",rs.getInt(1),"reserved",rs.getInt(2)) : null,
					skuId);
			if (inventory == null || ((Integer)inventory.get("reserved")) < quantity) {
				throw new IllegalStateException("Checkout reservation inventory is inconsistent");
			}
			jdbc.update("UPDATE inventories SET available_quantity=available_quantity+?,reserved_quantity=reserved_quantity-?,version=version+1 WHERE sku_id=?",
					quantity, quantity, skuId);
			jdbc.update("INSERT INTO inventory_movements(sku_id,payment_id,type,quantity,available_before,available_after,reserved_before,reserved_after,created_at) VALUES (?,?,'RELEASE',?,?,?,?,?,?)",
					skuId, paymentId, quantity,
					inventory.get("available"), ((Integer)inventory.get("available")) + quantity,
					inventory.get("reserved"), ((Integer)inventory.get("reserved")) - quantity,
					now());
		}
		jdbc.update("UPDATE member_coupons SET status='AVAILABLE',reserved_order_id=NULL WHERE reserved_order_id=? AND status='RESERVED'", orderId);
		jdbc.update("UPDATE payments SET status='FAILED',provider_status='EXPIRED',failure_code='CHECKOUT_EXPIRED',failed_at=? WHERE id=?", now(), paymentId);
		jdbc.update("UPDATE orders SET status='EXPIRED' WHERE id=?", orderId);
		return true;
	}

	private static Timestamp now() {
		return Timestamp.from(Instant.now());
	}
}
