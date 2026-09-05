package com.pawcycle.backend.commerce.delivery.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Clock;
import java.sql.Timestamp;
import org.springframework.stereotype.Repository;

@Repository
public class DeliveryPersistenceAdapter {
  private final JdbcTemplate queries;
  private final Clock clock;

  public DeliveryPersistenceAdapter(JdbcTemplate queries, Clock clock) {
    this.queries = queries;
    this.clock = clock;
  }

  public void createPreparing(long orderId) {
    queries.update(
        "INSERT INTO deliveries(order_id,status) VALUES (?,'PREPARING') ON DUPLICATE KEY UPDATE id=id",
        orderId);
  }

  public DeliveryLock findForUpdate(long deliveryId) {
    return queries
        .query(
            "SELECT id,order_id AS orderId,status FROM deliveries WHERE id=? FOR UPDATE",
            (rs, rowNumber) ->
                new DeliveryLock(rs.getLong("id"), rs.getLong("orderId"), rs.getString("status")),
            deliveryId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public long memberId(long orderId) {
    return queries.queryForObject("SELECT member_id FROM orders WHERE id=?", Long.class, orderId);
  }

  public void ship(long deliveryId, String carrier, String tracking) {
    queries.update(
        "UPDATE deliveries SET status='SHIPPED',carrier_code=?,tracking_number=?,failure_reason=NULL,failed_at=NULL,shipped_at=? WHERE id=?",
        carrier,
        tracking,
        now(),
        deliveryId);
  }

  public void transition(long deliveryId, String from, String to, String failureReason) {
    String timestampColumn = to.equals("DELIVERED") ? "delivered_at" : "failed_at";
    queries.update(
        "UPDATE deliveries SET status=?,failure_reason=?," + timestampColumn + "=? WHERE id=?",
        to,
        failureReason,
        now(),
        deliveryId);
  }

  public DeliveryView find(long deliveryId) {
    return queries
        .query(
            "SELECT id AS deliveryId,order_id AS orderId,status,carrier_code AS carrierCode,tracking_number AS trackingNumber,failure_reason AS failureReason,shipped_at AS shippedAt,delivered_at AS deliveredAt,failed_at AS failedAt,cancelled_at AS cancelledAt FROM deliveries WHERE id=?",
            (rs, rowNumber) ->
                new DeliveryView(
                    rs.getLong("deliveryId"),
                    rs.getLong("orderId"),
                    rs.getString("status"),
                    rs.getString("carrierCode"),
                    rs.getString("trackingNumber"),
                    rs.getString("failureReason"),
                    rs.getTimestamp("shippedAt"),
                    rs.getTimestamp("deliveredAt"),
                    rs.getTimestamp("failedAt"),
                    rs.getTimestamp("cancelledAt")),
            deliveryId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  private Timestamp now() {
    return Timestamp.from(clock.instant());
  }

  public record DeliveryLock(long deliveryId, long orderId, String status) {}
}
