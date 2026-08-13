package com.pawcycle.backend.commerce;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentReconciliationService {
	private final JdbcTemplate jdbc;
	private final TransactionTemplate tx;
	private final TossPaymentAdapter paymentProvider;
	private final TossBillingAdapter billingProvider;
	private final NotificationService notifications;
	private final CommerceService commerce;
	private final AdminAuditService audits;
	private final SubscriptionBillingService billingFailures;

	public PaymentReconciliationService(
			JdbcTemplate jdbc,
			org.springframework.transaction.PlatformTransactionManager manager,
			TossPaymentAdapter paymentProvider,
			TossBillingAdapter billingProvider,
			NotificationService notifications,
			CommerceService commerce,
			AdminAuditService audits,
			SubscriptionBillingService billingFailures) {
		this.jdbc = jdbc;
		this.tx = new TransactionTemplate(manager);
		this.paymentProvider = paymentProvider;
		this.billingProvider = billingProvider;
		this.notifications = notifications;
		this.commerce = commerce;
		this.audits = audits;
		this.billingFailures = billingFailures;
	}

	public Map<String,Object> reconcile(long id) { return reconcile(id,null); }

	public Map<String,Object> reconcile(long id,Long adminId) {
		Map<String,Object> work = tx.execute(status -> {
			Map<String,Object> row = one("SELECT id,type,status,provider_order_id,reconciliation_attempts FROM payments WHERE id=? FOR UPDATE", id);
			if (row == null) throw new CommerceException(404,"PAYMENT_NOT_FOUND","요청한 리소스를 찾을 수 없습니다.");
			boolean billingProcessing = "BILLING".equals(row.get("type")) && "PROCESSING".equals(row.get("status"));
			if (!billingProcessing && !"UNKNOWN".equals(row.get("status"))) throw new CommerceException(409,"PAYMENT_RECONCILIATION_NOT_ALLOWED","UNKNOWN 결제 또는 처리 중 Billing만 대사할 수 있습니다.");
			boolean configured = "BILLING".equals(row.get("type")) ? billingProvider.isConfigured() : paymentProvider.isConfigured();
			if (!configured) throw new CommerceException(503,"PAYMENT_PROVIDER_UNAVAILABLE","Toss 결제 Provider가 현재 환경에 구성되지 않았습니다.");
			int attempts = ((Number)row.get("reconciliation_attempts")).intValue();
			if (attempts >= 10) throw new CommerceException(409,"PAYMENT_RECONCILIATION_EXHAUSTED","결제 대사 횟수를 초과했습니다.");
			jdbc.update("UPDATE payments SET reconciliation_attempts=?,last_reconciled_at=? WHERE id=?", attempts + 1, now(), id);
			row.put("reconciliation_attempts", attempts + 1);
			return row;
		});

		ProviderResult result;
		try {
			if ("BILLING".equals(work.get("type"))) {
				TossBillingAdapter.ChargeResult observed = billingProvider.queryCharge((String)work.get("provider_order_id"));
				result = new ProviderResult(observed.status(), observed.providerStatus());
			} else {
				TossPaymentAdapter.ConfirmResult observed = paymentProvider.queryPayment((String)work.get("provider_order_id"));
				result = new ProviderResult(observed.status(), observed.providerStatus());
			}
		} catch (RuntimeException exception) {
			result = new ProviderResult("UNKNOWN","NO_RESPONSE");
		}

		ProviderResult observation = result;
		boolean billingFailure = Boolean.TRUE.equals(tx.execute(status -> {
			Map<String,Object> row = one("""
				SELECT payment.id,payment.order_id,payment.type,payment.status,payment.reconciliation_attempts,
				       orders.member_id,orders.source
				FROM payments payment JOIN orders ON orders.id=payment.order_id
				WHERE payment.id=? FOR UPDATE""", id);
			if (row == null) throw new CommerceException(404,"PAYMENT_NOT_FOUND","요청한 리소스를 찾을 수 없습니다.");
			boolean billingProcessing = "BILLING".equals(row.get("type")) && "PROCESSING".equals(row.get("status"));
			if (!billingProcessing && !"UNKNOWN".equals(row.get("status"))) return view(id);

			String resultStatus = observation.status();
			if ("SUCCEEDED".equals(resultStatus)) {
				completeSuccess(id,row,observation.providerStatus());
			} else if ("FAILED".equals(resultStatus)) {
				if ("BILLING".equals(row.get("type"))) {
					if (adminId != null) audits.append(adminId,"PAYMENT_RECONCILE","PAYMENT",id);
					return true;
				}
				completeFailure(id,row,observation.providerStatus());
			} else if (((Number)row.get("reconciliation_attempts")).intValue() >= 10) {
				jdbc.update("UPDATE orders SET status='PAYMENT_ACTION_REQUIRED' WHERE id=?", row.get("order_id"));
				notifications.create(((Number)row.get("member_id")).longValue(),"PAYMENT_ACTION_REQUIRED","PAYMENT",id);
			}
			if (adminId != null) audits.append(adminId,"PAYMENT_RECONCILE","PAYMENT",id);
			return false;
		}));
		if (billingFailure) {
			billingFailures.recordExplicitFailure(id,"RECONCILED_FAILED",observation.providerStatus());
			billingFailures.prepareNextAttempt(id);
		}
		return view(id);
	}

	private void completeSuccess(long paymentId,Map<String,Object> payment,String providerStatus) {
		long orderId = ((Number)payment.get("order_id")).longValue();
		for (Map<String,Object> item : jdbc.queryForList("SELECT sku_id,quantity FROM order_items WHERE order_id=? ORDER BY sku_id", orderId)) {
			long skuId = ((Number)item.get("sku_id")).longValue();
			int quantity = ((Number)item.get("quantity")).intValue();
			Map<String,Object> inventory = one("SELECT available_quantity,reserved_quantity FROM inventories WHERE sku_id=? FOR UPDATE", skuId);
			int availableBefore = ((Number)inventory.get("available_quantity")).intValue();
			int reservedBefore = ((Number)inventory.get("reserved_quantity")).intValue();
			jdbc.update("UPDATE inventories SET reserved_quantity=reserved_quantity-?,version=version+1 WHERE sku_id=?", quantity, skuId);
			jdbc.update("INSERT INTO inventory_movements(sku_id,payment_id,type,quantity,available_before,available_after,reserved_before,reserved_after,created_at) VALUES (?,?,'DEDUCT',?,?,?,?,?,?)", skuId, paymentId, quantity, availableBefore, availableBefore, reservedBefore, reservedBefore - quantity, now());
		}
		Timestamp paidAt = now();
		jdbc.update("UPDATE payments SET status='SUCCEEDED',provider_status=?,approved_at=? WHERE id=?", providerStatus, paidAt, paymentId);
		jdbc.update("UPDATE orders SET status='PAID',paid_at=? WHERE id=?", paidAt, orderId);
		jdbc.update("INSERT INTO deliveries(order_id,status) VALUES (?,'PREPARING') ON DUPLICATE KEY UPDATE id=id", orderId);
		jdbc.update("UPDATE member_coupons SET status='USED',used_at=? WHERE reserved_order_id=? AND status='RESERVED'", paidAt, orderId);
		long memberId = ((Number)payment.get("member_id")).longValue();
		if ("ONE_TIME".equals(payment.get("source"))) consumeCartForOrder(memberId,orderId);
		commerce.evaluateMembership(memberId);
		notifications.create(memberId,"ORDER_PAID","ORDER",orderId);
	}

	private void completeFailure(long paymentId,Map<String,Object> payment,String providerStatus) {
		long orderId = ((Number)payment.get("order_id")).longValue();
		for (Map<String,Object> item : jdbc.queryForList("SELECT sku_id,quantity FROM order_items WHERE order_id=? ORDER BY sku_id", orderId)) {
			long skuId = ((Number)item.get("sku_id")).longValue();
			int quantity = ((Number)item.get("quantity")).intValue();
			Map<String,Object> inventory = one("SELECT available_quantity,reserved_quantity FROM inventories WHERE sku_id=? FOR UPDATE", skuId);
			int availableBefore = ((Number)inventory.get("available_quantity")).intValue();
			int reservedBefore = ((Number)inventory.get("reserved_quantity")).intValue();
			jdbc.update("UPDATE inventories SET available_quantity=available_quantity+?,reserved_quantity=reserved_quantity-?,version=version+1 WHERE sku_id=?", quantity, quantity, skuId);
			jdbc.update("INSERT INTO inventory_movements(sku_id,payment_id,type,quantity,available_before,available_after,reserved_before,reserved_after,created_at) VALUES (?,?,'RELEASE',?,?,?,?,?,?)", skuId, paymentId, quantity, availableBefore, availableBefore + quantity, reservedBefore, reservedBefore - quantity, now());
		}
		jdbc.update("UPDATE payments SET status='FAILED',provider_status=?,failure_code='RECONCILED_FAILED',failed_at=? WHERE id=?", providerStatus, now(), paymentId);
		jdbc.update("UPDATE orders SET status='PAYMENT_FAILED' WHERE id=?", orderId);
		jdbc.update("UPDATE member_coupons SET status='AVAILABLE',reserved_order_id=NULL WHERE reserved_order_id=? AND status='RESERVED'", orderId);
	}

	private void consumeCartForOrder(long memberId,long orderId) {
		Long cartId = jdbc.query("SELECT id FROM carts WHERE member_id=? FOR UPDATE", rs -> rs.next() ? rs.getLong(1) : null, memberId);
		if (cartId == null) return;
		for (Map<String,Object> item : jdbc.queryForList("SELECT sku_id,quantity FROM order_items WHERE order_id=?", orderId)) {
			long skuId = ((Number)item.get("sku_id")).longValue();
			int purchased = ((Number)item.get("quantity")).intValue();
			Integer current = jdbc.query("SELECT quantity FROM cart_items WHERE cart_id=? AND sku_id=? FOR UPDATE", rs -> rs.next() ? rs.getInt(1) : null, cartId, skuId);
			if (current == null) continue;
			if (current <= purchased) jdbc.update("DELETE FROM cart_items WHERE cart_id=? AND sku_id=?", cartId, skuId);
			else jdbc.update("UPDATE cart_items SET quantity=? WHERE cart_id=? AND sku_id=?", current - purchased, cartId, skuId);
		}
		jdbc.update("UPDATE carts SET updated_at=? WHERE id=?", now(), cartId);
	}

	private Map<String,Object> view(long id) {
		return one("SELECT id AS paymentId,order_id AS orderId,status,reconciliation_attempts AS reconciliationAttempts,last_reconciled_at AS lastReconciledAt FROM payments WHERE id=?", id);
	}

	private static Timestamp now() { return Timestamp.from(Instant.now()); }
	private Map<String,Object> one(String sql,Object...args) {
		var rows = jdbc.queryForList(sql,args);
		return rows.isEmpty()?null:new LinkedHashMap<>(rows.getFirst());
	}
	private record ProviderResult(String status,String providerStatus) {}
}
