package com.pawcycle.backend.commerce;

import io.micrometer.core.instrument.Timer;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DeliveryService {
  private final NativeQueryExecutor jdbc;
  private final TransactionTemplate tx;
  private final NotificationService notifications;
  private final AdminAuditService audits;
  private final CommerceMetrics metrics;
  private final Clock clock;

  public DeliveryService(
      NativeQueryExecutor jdbc,
      org.springframework.transaction.PlatformTransactionManager manager,
      NotificationService notifications,
      AdminAuditService audits,
      CommerceMetrics metrics,
      Clock clock) {
    this.jdbc = jdbc;
    this.tx = new TransactionTemplate(manager);
    this.notifications = notifications;
    this.audits = audits;
    this.metrics = metrics;
    this.clock = clock;
  }

  /** Called from the successful-payment transaction; duplicate callbacks keep the one delivery. */
  public void createPreparing(long orderId) {
    jdbc.update(
        "INSERT INTO deliveries(order_id,status) VALUES (?,'PREPARING') ON DUPLICATE KEY UPDATE"
            + " id=id",
        orderId);
  }

  public CommercePayload ship(long id, String carrier, String tracking) {
    return ship(null, id, carrier, tracking);
  }

  public CommercePayload ship(Long adminId, long id, String carrier, String tracking) {
    Timer.Sample sample = metrics.timer();
    try {
      return CommercePayload.from(tx.execute(
          status -> {
            Map<String, Object> row =
                one("SELECT id,order_id,status FROM deliveries WHERE id=? FOR UPDATE", id);
            if (row == null)
              throw new CommerceException(404, "DELIVERY_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
            if (!"PREPARING".equals(row.get("status")) && !"FAILED".equals(row.get("status")))
              throw new CommerceException(409, "DELIVERY_STATE_CONFLICT", "배송 상태를 전이할 수 없습니다.");
            jdbc.update(
                "UPDATE deliveries SET"
                    + " status='SHIPPED',carrier_code=?,tracking_number=?,failure_reason=NULL,failed_at=NULL,shipped_at=?"
                    + " WHERE id=?",
                carrier,
                tracking,
                Timestamp.from(clock.instant()),
                id);
            long member =
                jdbc.queryForObject(
                    "SELECT member_id FROM orders WHERE id=?",
                    Long.class,
                    ((Number) row.get("order_id")).longValue());
            notifications.create(member, "ORDER_SHIPPED", "DELIVERY", id);
            metrics.count("delivery.transition", "SHIPPED");
            if (adminId != null) audits.append(adminId, "DELIVERY_SHIP", "DELIVERY", id);
            return view(id);
          }));
    } finally {
      metrics.stop(sample, "delivery.transition");
    }
  }

  public CommercePayload complete(long id) {
    return complete(null, id);
  }

  public CommercePayload complete(Long adminId, long id) {
    return CommercePayload.from(
        transition(adminId, id, "SHIPPED", "DELIVERED", null, "ORDER_DELIVERED", "DELIVERY_COMPLETE"));
  }

  public CommercePayload fail(long id, String reason) {
    return fail(null, id, reason);
  }

  public CommercePayload fail(Long adminId, long id, String reason) {
    return CommercePayload.from(
        transition(adminId, id, "SHIPPED", "FAILED", reason, null, "DELIVERY_FAIL"));
  }

  private Map<String, Object> transition(
      Long adminId,
      long id,
      String from,
      String to,
      String failure,
      String notification,
      String auditAction) {
    Timer.Sample sample = metrics.timer();
    try {
      return tx.execute(
          status -> {
            Map<String, Object> row =
                one("SELECT id,order_id,status FROM deliveries WHERE id=? FOR UPDATE", id);
            if (row == null)
              throw new CommerceException(404, "DELIVERY_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
            if (!from.equals(row.get("status")))
              throw new CommerceException(409, "DELIVERY_STATE_CONFLICT", "배송 상태를 전이할 수 없습니다.");
            Timestamp now = Timestamp.from(clock.instant());
            String column = to.equals("DELIVERED") ? "delivered_at" : "failed_at";
            jdbc.update(
                "UPDATE deliveries SET status=?,failure_reason=?," + column + "=? WHERE id=?",
                to,
                failure,
                now,
                id);
            if (notification != null) {
              long member =
                  jdbc.queryForObject(
                      "SELECT member_id FROM orders WHERE id=?",
                      Long.class,
                      ((Number) row.get("order_id")).longValue());
              notifications.create(member, notification, "DELIVERY", id);
            }
            metrics.count("delivery.transition", to);
            if (adminId != null) audits.append(adminId, auditAction, "DELIVERY", id);
            return view(id);
          });
    } finally {
      metrics.stop(sample, "delivery.transition");
    }
  }

  private Map<String, Object> view(long id) {
    return one(
        "SELECT id AS deliveryId,order_id AS orderId,status,carrier_code AS"
            + " carrierCode,tracking_number AS trackingNumber,failure_reason AS"
            + " failureReason,shipped_at AS shippedAt,delivered_at AS deliveredAt,failed_at AS"
            + " failedAt,cancelled_at AS cancelledAt FROM deliveries WHERE id=?",
        id);
  }

  private Map<String, Object> one(String sql, Object... args) {
    var rows = jdbc.queryForList(sql, args);
    return rows.isEmpty() ? null : new LinkedHashMap<>(rows.getFirst());
  }
}
