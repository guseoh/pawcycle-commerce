package com.pawcycle.backend.commerce;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Executes at most the READY attempt selected at the start of a processor cycle. */
@Service
public class SubscriptionBillingProcessor {
	private final JdbcTemplate jdbc; private final TransactionTemplate tx; private final TossBillingAdapter provider;
	private final SubscriptionBillingService retries; private final DeliveryService deliveries; private final NotificationService notifications;
	public SubscriptionBillingProcessor(JdbcTemplate jdbc, org.springframework.transaction.PlatformTransactionManager manager, TossBillingAdapter provider, SubscriptionBillingService retries, DeliveryService deliveries, NotificationService notifications) { this.jdbc=jdbc; this.tx=new TransactionTemplate(manager); this.provider=provider; this.retries=retries; this.deliveries=deliveries; this.notifications=notifications; }
	public int processReadyPayments() {
		List<Long> ready=jdbc.query("SELECT id FROM payments WHERE type='BILLING' AND status='READY' ORDER BY id",(rs,row)->rs.getLong(1));
		for(long id:ready) process(id);
		return ready.size();
	}
	public void process(long paymentId) {
		if (!provider.isConfigured()) throw new CommerceException(503,"PAYMENT_PROVIDER_UNAVAILABLE","Toss Billing Provider가 현재 환경에 구성되지 않았습니다.");
		Map<String,Object> work=tx.execute(status -> {
			Map<String,Object> row=one("""
				SELECT payment.id,payment.order_id,payment.provider_order_id,payment.amount,payment.status,method.billing_key
				FROM payments payment JOIN orders orders ON orders.id=payment.order_id
				JOIN billing_payment_methods method ON method.member_id=orders.member_id AND method.status='ACTIVE'
				WHERE payment.id=? FOR UPDATE""",paymentId);
			if (row==null) throw new CommerceException(409,"BILLING_METHOD_MISSING","활성 Billing 수단이 없습니다.");
			if (!"READY".equals(row.get("status"))) return null;
			jdbc.update("UPDATE payments SET status='PROCESSING' WHERE id=?",paymentId);
			return row;
		});
		if (work==null) return;
		TossBillingAdapter.ChargeResult result;
		try { result=provider.charge((String)work.get("billing_key"),(String)work.get("provider_order_id"),(java.math.BigDecimal)work.get("amount")); }
		catch (RuntimeException exception) { result=new TossBillingAdapter.ChargeResult("UNKNOWN","NO_RESPONSE"); }
		if ("SUCCEEDED".equals(result.status())) completeSuccess(paymentId,result.providerStatus());
		else if ("FAILED".equals(result.status())) { retries.recordExplicitFailure(paymentId,"TOSS_REJECTED"); retries.prepareNextAttempt(paymentId); }
		else markUnknown(paymentId,result.providerStatus());
	}
	private void completeSuccess(long paymentId,String providerStatus) { tx.executeWithoutResult(status -> {
		Map<String,Object> payment=one("SELECT payment.id,payment.order_id,orders.member_id FROM payments payment JOIN orders orders ON orders.id=payment.order_id WHERE payment.id=? AND payment.status='PROCESSING' FOR UPDATE",paymentId); if(payment==null)return;
		long orderId=((Number)payment.get("order_id")).longValue();
		for(Map<String,Object> item:jdbc.queryForList("SELECT sku_id,quantity FROM order_items WHERE order_id=?",orderId)) deduct(((Number)item.get("sku_id")).longValue(),((Number)item.get("quantity")).intValue(),paymentId);
		Timestamp now=Timestamp.from(Instant.now());jdbc.update("UPDATE payments SET status='SUCCEEDED',provider_status=?,approved_at=? WHERE id=?",providerStatus,now,paymentId);jdbc.update("UPDATE orders SET status='PAID',paid_at=? WHERE id=?",now,orderId);deliveries.createPreparing(orderId);notifications.create(((Number)payment.get("member_id")).longValue(),"ORDER_PAID","ORDER",orderId);
	}); }
	private void markUnknown(long paymentId,String providerStatus) { tx.executeWithoutResult(status -> jdbc.update("UPDATE payments SET status='UNKNOWN',provider_status=?,failure_code='PROVIDER_RESULT_UNKNOWN' WHERE id=? AND status='PROCESSING'",providerStatus,paymentId)); }
	private void deduct(long skuId,int quantity,long paymentId){Map<String,Object> inv=one("SELECT available_quantity,reserved_quantity FROM inventories WHERE sku_id=? FOR UPDATE",skuId);jdbc.update("UPDATE inventories SET reserved_quantity=reserved_quantity-?,version=version+1 WHERE sku_id=?",quantity,skuId);jdbc.update("INSERT INTO inventory_movements(sku_id,payment_id,type,quantity,available_before,available_after,reserved_before,reserved_after,created_at) VALUES (?,?,'DEDUCT',?,?,?,?,?,?)",skuId,paymentId,quantity,inv.get("available_quantity"),inv.get("available_quantity"),inv.get("reserved_quantity"),((Number)inv.get("reserved_quantity")).intValue()-quantity,Timestamp.from(Instant.now()));}
	private Map<String,Object> one(String sql,Object...args){var rows=jdbc.queryForList(sql,args);return rows.isEmpty()?null:new LinkedHashMap<>(rows.getFirst());}
}
