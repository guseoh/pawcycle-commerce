package com.pawcycle.backend.commerce;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ReturnService {
  private final NativeQueryExecutor jdbc;
  private final TransactionTemplate tx;
  private final NotificationService notifications;
  private final AdminAuditService audits;
  private final InventoryService inventory;
  private final int requestDays;
  private final Clock clock;

  public ReturnService(
      NativeQueryExecutor jdbc,
      org.springframework.transaction.PlatformTransactionManager manager,
      NotificationService notifications,
      AdminAuditService audits,
      InventoryService inventory,
      @Value("${pawcycle.commerce.return-request-days:7}") int requestDays,
      Clock clock) {
    this.jdbc = jdbc;
    this.tx = new TransactionTemplate(manager);
    this.notifications = notifications;
    this.audits = audits;
    this.inventory = inventory;
    this.requestDays = requestDays;
    this.clock = clock;
  }

  public CommercePayload request(long memberId, long orderId, String reason) {
    return CommercePayload.from(tx.execute(
        status -> {
          Map<String, Object> order =
              one(
                  "SELECT id,status FROM orders WHERE id=? AND member_id=? FOR UPDATE",
                  orderId,
                  memberId);
          if (order == null)
            throw new CommerceException(404, "ORDER_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
          Map<String, Object> existing =
              one(
                  "SELECT id AS returnId,status,reason,rejection_reason AS"
                      + " rejectionReason,restock,requested_at AS requestedAt FROM order_returns"
                      + " WHERE order_id=? FOR UPDATE",
                  orderId);
          if (existing != null) return existing;
          if (one("SELECT id FROM order_cancellations WHERE order_id=? FOR UPDATE", orderId)
              != null) {
            throw new CommerceException(409, "RETURN_NOT_ALLOWED", "취소가 진행된 주문은 반품할 수 없습니다.");
          }
          Map<String, Object> delivery =
              one(
                  "SELECT status,delivered_at FROM deliveries WHERE order_id=? FOR UPDATE",
                  orderId);
          Timestamp delivered = delivery == null ? null : (Timestamp) delivery.get("delivered_at");
          if (!"PAID".equals(order.get("status"))
              || delivery == null
              || !"DELIVERED".equals(delivery.get("status"))
              || delivered == null
              || delivered.toInstant().plus(requestDays, ChronoUnit.DAYS).isBefore(clock.instant())) {
            throw new CommerceException(409, "RETURN_NOT_ALLOWED", "반품 요청 가능 기간이 아닙니다.");
          }
          Timestamp now = Timestamp.from(clock.instant());
          jdbc.update(
              "INSERT INTO order_returns(order_id,status,reason,requested_at) VALUES"
                  + " (?,'REQUESTED',?,?)",
              orderId,
              reason,
              now);
          long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
          return one(
              "SELECT id AS returnId,status,reason,rejection_reason AS"
                  + " rejectionReason,restock,requested_at AS requestedAt FROM order_returns WHERE"
                  + " id=?",
              id);
        }));
  }

  public CommercePayload approve(long adminId, long id) {
    return CommercePayload.from(decide(adminId, id, "APPROVED", null));
  }

  public CommercePayload reject(long adminId, long id, String reason) {
    return CommercePayload.from(decide(adminId, id, "REJECTED", reason));
  }

  private Map<String, Object> decide(long adminId, long id, String status, String reason) {
    return tx.execute(
        txStatus -> {
          Map<String, Object> row =
              one(
                  "SELECT"
                      + " return_order.id,return_order.order_id,return_order.status,orders.member_id"
                      + " FROM order_returns return_order JOIN orders ON"
                      + " orders.id=return_order.order_id WHERE return_order.id=? FOR UPDATE",
                  id);
          if (row == null)
            throw new CommerceException(404, "RETURN_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
          if (!"REQUESTED".equals(row.get("status")))
            throw new CommerceException(409, "RETURN_STATE_CONFLICT", "반품 상태를 전이할 수 없습니다.");
          jdbc.update(
              "UPDATE order_returns SET"
                  + " status=?,rejection_reason=?,decided_at=?,decided_by_admin_id=? WHERE id=?",
              status,
              reason,
              Timestamp.from(clock.instant()),
              adminId,
              id);
          notifications.create(
              ((Number) row.get("member_id")).longValue(),
              "APPROVED".equals(status) ? "RETURN_APPROVED" : "RETURN_REJECTED",
              "RETURN",
              id);
          audits.append(
              adminId,
              "APPROVED".equals(status) ? "RETURN_APPROVE" : "RETURN_REJECT",
              "RETURN",
              id);
          return one(
              "SELECT id AS returnId,status,rejection_reason AS rejectionReason,decided_at AS"
                  + " decidedAt FROM order_returns WHERE id=?",
              id);
        });
  }

  public CommercePayload receive(long adminId, long id, boolean restock) {
    return CommercePayload.from(tx.execute(
        status -> {
          Map<String, Object> row =
              one("SELECT id,order_id,status FROM order_returns WHERE id=? FOR UPDATE", id);
          if (row == null)
            throw new CommerceException(404, "RETURN_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
          if (!"APPROVED".equals(row.get("status")))
            throw new CommerceException(409, "RETURN_STATE_CONFLICT", "승인된 반품만 수령할 수 있습니다.");
          long orderId = ((Number) row.get("order_id")).longValue();
          Timestamp now = Timestamp.from(clock.instant());
          if (restock) restore(orderId, id);
          jdbc.update(
              "UPDATE order_returns SET"
                  + " status='REFUND_PENDING',restock=?,received_at=?,received_by_admin_id=? WHERE"
                  + " id=?",
              restock,
              now,
              adminId,
              id);
          jdbc.update(
              "INSERT INTO"
                  + " refunds(order_id,source,return_id,status,amount,provider,idempotency_key,attempt_no,requested_at)"
                  + " SELECT ?,'RETURN',?,'READY',payment_amount,'TOSS',?,1,? FROM orders WHERE"
                  + " id=?",
              orderId,
              id,
              "refund-" + UUID.randomUUID(),
              now,
              orderId);
          audits.append(adminId, "RETURN_RECEIVE", "RETURN", id);
          return one(
              "SELECT id AS returnId,status,restock,received_at AS receivedAt FROM order_returns"
                  + " WHERE id=?",
              id);
        }));
  }

  private void restore(long orderId, long returnId) {
    for (Map<String, Object> item :
        jdbc.queryForList(
            "SELECT sku_id,quantity FROM order_items WHERE order_id=? ORDER BY sku_id", orderId)) {
      long sku = ((Number) item.get("sku_id")).longValue();
      int qty = ((Number) item.get("quantity")).intValue();
      inventory.restoreReturn(sku, qty, returnId);
    }
  }

  private Map<String, Object> one(String sql, Object... args) {
    var rows = jdbc.queryForList(sql, args);
    return rows.isEmpty() ? null : new LinkedHashMap<>(rows.getFirst());
  }
}
