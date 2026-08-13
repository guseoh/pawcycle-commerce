package com.pawcycle.backend.commerce;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Inventory mutations and their movement audit are one transaction owned by the caller. */
@Service
public class InventoryService {
	private final JdbcTemplate jdbc;
	private final Clock clock;

	public InventoryService(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	public void reserve(long skuId, int quantity, long paymentId) {
		requirePositiveQuantity(quantity);
		Map<String, Object> inventory = one("SELECT available_quantity,reserved_quantity,version FROM inventories WHERE sku_id=?", skuId);
		if (inventory == null || number(inventory, "available_quantity") < quantity) {
			throw new CommerceException(409, "INVENTORY_INSUFFICIENT", "재고가 부족합니다.");
		}
		int changed = jdbc.update("UPDATE inventories SET available_quantity=available_quantity-?,reserved_quantity=reserved_quantity+?,version=version+1 WHERE sku_id=? AND version=? AND available_quantity>=?",
				quantity, quantity, skuId, number(inventory, "version"), quantity);
		if (changed != 1) throw new CommerceException(409, "INVENTORY_CONFLICT", "재고가 변경되었습니다.");
		movement(skuId, paymentId, "RESERVE", quantity, number(inventory, "available_quantity"), number(inventory, "available_quantity") - quantity,
				number(inventory, "reserved_quantity"), number(inventory, "reserved_quantity") + quantity, null, null);
	}

	public void release(long skuId, int quantity, long paymentId) {
		requirePositiveQuantity(quantity);
		Map<String, Object> inventory = lock(skuId);
		requireReservedQuantity(inventory, quantity);
		jdbc.update("UPDATE inventories SET available_quantity=available_quantity+?,reserved_quantity=reserved_quantity-?,version=version+1 WHERE sku_id=?", quantity, quantity, skuId);
		movement(skuId, paymentId, "RELEASE", quantity, number(inventory, "available_quantity"), number(inventory, "available_quantity") + quantity,
				number(inventory, "reserved_quantity"), number(inventory, "reserved_quantity") - quantity, null, null);
	}

	public void deduct(long skuId, int quantity, long paymentId) {
		requirePositiveQuantity(quantity);
		Map<String, Object> inventory = lock(skuId);
		requireReservedQuantity(inventory, quantity);
		jdbc.update("UPDATE inventories SET reserved_quantity=reserved_quantity-?,version=version+1 WHERE sku_id=?", quantity, skuId);
		movement(skuId, paymentId, "DEDUCT", quantity, number(inventory, "available_quantity"), number(inventory, "available_quantity"),
				number(inventory, "reserved_quantity"), number(inventory, "reserved_quantity") - quantity, null, null);
	}

	public void restoreCancellation(long skuId, int quantity, long cancellationId) {
		restore(skuId, quantity, cancellationId, "CANCEL_RESTORE", "cancellation_id");
	}

	public void restoreReturn(long skuId, int quantity, long returnId) {
		restore(skuId, quantity, returnId, "RETURN_RESTORE", "return_id");
	}

	public void adjust(long skuId, int delta) {
		Map<String, Object> inventory = lockForAdjustment(skuId);
		int available = (int) number(inventory, "available_quantity");
		if (available + delta < 0) throw new CommerceException(409, "INVENTORY_INSUFFICIENT", "재고가 부족합니다.");
		jdbc.update("UPDATE inventories SET available_quantity=available_quantity+?,version=version+1 WHERE sku_id=?", delta, skuId);
		if (delta != 0) movement(skuId, null, "ADMIN_ADJUST", Math.abs(delta), available, available + delta,
				number(inventory, "reserved_quantity"), number(inventory, "reserved_quantity"), null, null);
	}

	private void restore(long skuId, int quantity, long sourceId, String type, String sourceColumn) {
		requirePositiveQuantity(quantity);
		Map<String, Object> inventory = lock(skuId);
		jdbc.update("UPDATE inventories SET available_quantity=available_quantity+?,version=version+1 WHERE sku_id=?", quantity, skuId);
		String sql = "INSERT INTO inventory_movements(sku_id,payment_id,type,quantity,available_before,available_after,reserved_before,reserved_after," + sourceColumn + ",source_id,created_at) VALUES (?,NULL,?,?,?,?,?,?,?,?,?)";
		jdbc.update(sql, skuId, type, quantity, number(inventory, "available_quantity"), number(inventory, "available_quantity") + quantity,
				number(inventory, "reserved_quantity"), number(inventory, "reserved_quantity"), sourceId, sourceId, now());
	}

	private Map<String, Object> lock(long skuId) {
		Map<String, Object> inventory = one("SELECT available_quantity,reserved_quantity FROM inventories WHERE sku_id=? FOR UPDATE", skuId);
		if (inventory == null) throw new IllegalStateException("Inventory is missing");
		return inventory;
	}

	private Map<String, Object> lockForAdjustment(long skuId) {
		Map<String, Object> inventory = one("SELECT available_quantity,reserved_quantity FROM inventories WHERE sku_id=? FOR UPDATE", skuId);
		if (inventory == null) throw new CommerceException(404, "INVENTORY_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
		return inventory;
	}

	private static void requirePositiveQuantity(int quantity) {
		if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
	}

	private static void requireReservedQuantity(Map<String, Object> inventory, int quantity) {
		if (number(inventory, "reserved_quantity") < quantity) {
			throw new IllegalStateException("Reserved inventory is inconsistent");
		}
	}

	private void movement(long skuId, Long paymentId, String type, int quantity, long availableBefore, long availableAfter, long reservedBefore, long reservedAfter, Long cancellationId, Long returnId) {
		jdbc.update("INSERT INTO inventory_movements(sku_id,payment_id,type,quantity,available_before,available_after,reserved_before,reserved_after,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
				skuId, paymentId, type, quantity, availableBefore, availableAfter, reservedBefore, reservedAfter, now());
	}

	private Map<String, Object> one(String sql, Object... args) {
		var rows = jdbc.queryForList(sql, args);
		return rows.isEmpty() ? null : new LinkedHashMap<>(rows.getFirst());
	}
	private Timestamp now() { return Timestamp.from(clock.instant()); }
	private static long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
}
