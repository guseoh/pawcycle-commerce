package com.pawcycle.backend.commerce;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Per-payment transaction boundary for expiry traversal. */
@Service
class CheckoutExpirationProcessor {
	private final JdbcTemplate jdbc;
	private final InventoryService inventory;
	private final Clock clock;

	CheckoutExpirationProcessor(JdbcTemplate jdbc, InventoryService inventory, Clock clock) {
		this.jdbc = jdbc;
		this.inventory = inventory;
		this.clock = clock;
	}

	@Transactional
	public boolean expire(long paymentId) {
		Map<String, Object> payment = one("SELECT payment.id,payment.order_id,payment.status,orders.status AS order_status FROM payments payment JOIN orders ON orders.id=payment.order_id WHERE payment.id=? FOR UPDATE", paymentId);
		if (payment == null || !"READY".equals(payment.get("status")) || !"PAYMENT_PENDING".equals(payment.get("order_status"))) return false;
		long orderId = number(payment, "order_id");
		for (Map<String, Object> item : jdbc.queryForList("SELECT sku_id,quantity FROM order_items WHERE order_id=?", orderId)) {
			inventory.release(number(item, "sku_id"), (int) number(item, "quantity"), paymentId);
		}
		jdbc.update("UPDATE member_coupons SET status='AVAILABLE',reserved_order_id=NULL WHERE reserved_order_id=? AND status='RESERVED'", orderId);
		Timestamp now = Timestamp.from(clock.instant());
		jdbc.update("UPDATE payments SET status='FAILED',provider_status='EXPIRED',failure_code='CHECKOUT_EXPIRED',failed_at=? WHERE id=?", now, paymentId);
		jdbc.update("UPDATE orders SET status='EXPIRED' WHERE id=?", orderId);
		return true;
	}

	private Map<String, Object> one(String sql, Object... args) { var rows = jdbc.queryForList(sql, args); return rows.isEmpty() ? null : new LinkedHashMap<>(rows.getFirst()); }
	private static long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
}
