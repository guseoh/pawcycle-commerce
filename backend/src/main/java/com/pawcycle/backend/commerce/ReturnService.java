package com.pawcycle.backend.commerce;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ReturnService {
	private final JdbcTemplate jdbc; private final TransactionTemplate tx; private final NotificationService notifications; private final int requestDays;
	public ReturnService(JdbcTemplate jdbc, org.springframework.transaction.PlatformTransactionManager manager, NotificationService notifications, @Value("${pawcycle.commerce.return-request-days:7}") int requestDays) { this.jdbc=jdbc; this.tx=new TransactionTemplate(manager); this.notifications=notifications; this.requestDays=requestDays; }
	public Map<String,Object> request(long memberId,long orderId,String reason) { return tx.execute(s -> {
		Map<String,Object> existing=one("SELECT return_order.id AS returnId,return_order.status,return_order.reason,return_order.rejection_reason AS rejectionReason,return_order.restock,return_order.requested_at AS requestedAt FROM order_returns return_order JOIN orders ON orders.id=return_order.order_id WHERE return_order.order_id=? AND orders.member_id=?",orderId,memberId); if(existing!=null)return existing;
		Map<String,Object> delivery=one("SELECT delivered_at FROM deliveries delivery JOIN orders ON orders.id=delivery.order_id WHERE delivery.order_id=? AND orders.member_id=? FOR UPDATE",orderId,memberId); if(delivery==null) throw new CommerceException(404,"ORDER_NOT_FOUND","요청한 리소스를 찾을 수 없습니다."); Timestamp delivered=(Timestamp)delivery.get("delivered_at"); if(delivered==null || delivered.toInstant().plus(requestDays,ChronoUnit.DAYS).isBefore(Instant.now())) throw new CommerceException(409,"RETURN_NOT_ALLOWED","반품 요청 가능 기간이 아닙니다.");
		Timestamp now=Timestamp.from(Instant.now()); jdbc.update("INSERT INTO order_returns(order_id,status,reason,requested_at) VALUES (?,'REQUESTED',?,?)",orderId,reason,now); long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class); return one("SELECT id AS returnId,status,reason,requested_at AS requestedAt FROM order_returns WHERE id=?",id);
	}); }
	public Map<String,Object> approve(long adminId,long id) { return decide(adminId,id,"APPROVED",null); }
	public Map<String,Object> reject(long adminId,long id,String reason) { return decide(adminId,id,"REJECTED",reason); }
	private Map<String,Object> decide(long adminId,long id,String status,String reason) { return tx.execute(s -> { Map<String,Object> row=one("SELECT return_order.id,return_order.order_id,return_order.status,orders.member_id FROM order_returns return_order JOIN orders ON orders.id=return_order.order_id WHERE return_order.id=? FOR UPDATE",id); if(row==null)throw new CommerceException(404,"RETURN_NOT_FOUND","요청한 리소스를 찾을 수 없습니다."); if(!"REQUESTED".equals(row.get("status")))throw new CommerceException(409,"RETURN_STATE_CONFLICT","반품 상태를 전이할 수 없습니다."); jdbc.update("UPDATE order_returns SET status=?,rejection_reason=?,decided_at=?,decided_by_admin_id=? WHERE id=?",status,reason,Timestamp.from(Instant.now()),adminId,id); notifications.create(((Number)row.get("member_id")).longValue(),"APPROVED".equals(status)?"RETURN_APPROVED":"RETURN_REJECTED","RETURN",id); return one("SELECT id AS returnId,status,rejection_reason AS rejectionReason,decided_at AS decidedAt FROM order_returns WHERE id=?",id); }); }
	public Map<String,Object> receive(long adminId,long id,boolean restock) { return tx.execute(s -> { Map<String,Object> row=one("SELECT id,order_id,status FROM order_returns WHERE id=? FOR UPDATE",id); if(row==null)throw new CommerceException(404,"RETURN_NOT_FOUND","요청한 리소스를 찾을 수 없습니다."); if(!"APPROVED".equals(row.get("status")))throw new CommerceException(409,"RETURN_STATE_CONFLICT","승인된 반품만 수령할 수 있습니다."); long orderId=((Number)row.get("order_id")).longValue(); Timestamp now=Timestamp.from(Instant.now()); if(restock) restore(orderId,id); jdbc.update("UPDATE order_returns SET status='REFUND_PENDING',restock=?,received_at=?,received_by_admin_id=? WHERE id=?",restock,now,adminId,id); jdbc.update("INSERT INTO refunds(order_id,source,return_id,status,amount,provider,idempotency_key,attempt_no,requested_at) SELECT ?,'RETURN',?,'READY',payment_amount,'TOSS',?,1,? FROM orders WHERE id=?",orderId,id,"refund-"+UUID.randomUUID(),now,orderId); return one("SELECT id AS returnId,status,restock,received_at AS receivedAt FROM order_returns WHERE id=?",id); }); }
	private void restore(long orderId,long returnId) { for(Map<String,Object> item:jdbc.queryForList("SELECT sku_id,quantity FROM order_items WHERE order_id=?",orderId)) { long sku=((Number)item.get("sku_id")).longValue();int qty=((Number)item.get("quantity")).intValue();Map<String,Object> inv=one("SELECT available_quantity,reserved_quantity FROM inventories WHERE sku_id=? FOR UPDATE",sku);jdbc.update("UPDATE inventories SET available_quantity=available_quantity+?,version=version+1 WHERE sku_id=?",qty,sku);jdbc.update("INSERT INTO inventory_movements(sku_id,payment_id,type,quantity,available_before,available_after,reserved_before,reserved_after,return_id,source_id,created_at) VALUES (?,?, 'RETURN_RESTORE',?,?,?,?,?,?,?)",sku,null,qty,inv.get("available_quantity"),((Number)inv.get("available_quantity")).intValue()+qty,inv.get("reserved_quantity"),inv.get("reserved_quantity"),returnId,returnId,Timestamp.from(Instant.now())); } }
	private Map<String,Object> one(String sql,Object...args){var rows=jdbc.queryForList(sql,args);return rows.isEmpty()?null:new LinkedHashMap<>(rows.getFirst());}
}
