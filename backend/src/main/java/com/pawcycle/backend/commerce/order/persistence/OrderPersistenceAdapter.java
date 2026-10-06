package com.pawcycle.backend.commerce.order.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class OrderPersistenceAdapter {
  private final JdbcTemplate queries;
  public OrderPersistenceAdapter(JdbcTemplate queries) {
    this.queries = queries;
  }

  public List<Summary> findOrders(long memberId) {
    return queries.query(
        "SELECT id AS orderId,order_number AS orderNumber,source,status,payment_amount AS paymentAmount,created_at AS createdAt,paid_at AS paidAt FROM orders WHERE member_id=? ORDER BY id DESC",
        (rs, rowNumber) ->
            new Summary(
                rs.getLong("orderId"),
                rs.getString("orderNumber"),
                rs.getString("source"),
                rs.getString("status"),
                rs.getBigDecimal("paymentAmount"),
                rs.getTimestamp("createdAt"),
                rs.getTimestamp("paidAt")),
        memberId);
  }

  public OrderView findOrder(long memberId, long orderId) {
    List<OrderView> orders =
        queries.query(
            "SELECT id AS orderId,order_number AS orderNumber,source,status,original_amount AS originalAmount,discount_amount AS discountAmount,shipping_fee AS shippingFee,payment_amount AS paymentAmount,recipient_name AS recipientName,recipient_phone AS recipientPhone,postal_code AS postalCode,address_line1 AS addressLine1,address_line2 AS addressLine2,created_at AS createdAt,paid_at AS paidAt FROM orders WHERE id=? AND member_id=?",
            (rs, rowNumber) ->
                new OrderView(
                    rs.getLong("orderId"),
                    rs.getString("orderNumber"),
                    rs.getString("source"),
                    rs.getString("status"),
                    rs.getBigDecimal("originalAmount"),
                    rs.getBigDecimal("discountAmount"),
                    rs.getBigDecimal("shippingFee"),
                    rs.getBigDecimal("paymentAmount"),
                    rs.getString("recipientName"),
                    rs.getString("recipientPhone"),
                    rs.getString("postalCode"),
                    rs.getString("addressLine1"),
                    rs.getString("addressLine2"),
                    rs.getTimestamp("createdAt"),
                    rs.getTimestamp("paidAt"),
                    findItems(orderId),
                    findPayment(orderId),
                    findDelivery(orderId),
                    findCancellation(orderId),
                    findReturn(orderId),
                    findRefunds(orderId)),
            orderId,
            memberId);
    return orders.stream().findFirst().orElse(null);
  }

  private List<OrderView.Item> findItems(long orderId) {
    return queries.query(
        "SELECT sku_id AS skuId,snapshot_quality AS snapshotQuality,sku_code_snapshot AS skuCodeSnapshot,product_name_snapshot AS productNameSnapshot,sku_name_snapshot AS skuNameSnapshot,unit_price AS unitPrice,quantity,line_amount AS lineAmount FROM order_items WHERE order_id=? ORDER BY id",
        (rs, rowNumber) ->
            new OrderView.Item(
                rs.getLong("skuId"),
                rs.getString("snapshotQuality"),
                rs.getString("skuCodeSnapshot"),
                rs.getString("productNameSnapshot"),
                rs.getString("skuNameSnapshot"),
                rs.getBigDecimal("unitPrice"),
                rs.getInt("quantity"),
                rs.getBigDecimal("lineAmount")),
        orderId);
  }

  private OrderView.Payment findPayment(long orderId) {
    return queries
        .query(
            "SELECT id AS paymentId,type,provider,status,amount,attempt_no AS attemptNo,provider_status AS providerStatus FROM payments WHERE order_id=? ORDER BY attempt_no DESC LIMIT 1",
            (rs, rowNumber) ->
                new OrderView.Payment(
                    rs.getLong("paymentId"),
                    rs.getString("type"),
                    rs.getString("provider"),
                    rs.getString("status"),
                    rs.getBigDecimal("amount"),
                    rs.getInt("attemptNo"),
                    rs.getString("providerStatus")),
            orderId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  private OrderView.Delivery findDelivery(long orderId) {
    return queries
        .query(
            "SELECT id AS deliveryId,order_id AS orderId,status,carrier_code AS carrierCode,tracking_number AS trackingNumber,failure_reason AS failureReason,shipped_at AS shippedAt,delivered_at AS deliveredAt,failed_at AS failedAt,cancelled_at AS cancelledAt FROM deliveries WHERE order_id=?",
            (rs, rowNumber) ->
                new OrderView.Delivery(
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
            orderId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  private OrderView.Cancellation findCancellation(long orderId) {
    return queries
        .query(
            "SELECT id AS cancellationId,status,reason,requested_at AS requestedAt,completed_at AS completedAt FROM order_cancellations WHERE order_id=?",
            (rs, rowNumber) ->
                new OrderView.Cancellation(
                    rs.getLong("cancellationId"),
                    rs.getString("status"),
                    rs.getString("reason"),
                    rs.getTimestamp("requestedAt"),
                    rs.getTimestamp("completedAt")),
            orderId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  private OrderView.ReturnRequest findReturn(long orderId) {
    return queries
        .query(
            "SELECT id AS returnId,status,reason,rejection_reason AS rejectionReason,restock,requested_at AS requestedAt,received_at AS receivedAt,completed_at AS completedAt FROM order_returns WHERE order_id=?",
            (rs, rowNumber) ->
                new OrderView.ReturnRequest(
                    rs.getLong("returnId"),
                    rs.getString("status"),
                    rs.getString("reason"),
                    rs.getString("rejectionReason"),
                    nullableBoolean(rs, "restock"),
                    rs.getTimestamp("requestedAt"),
                    rs.getTimestamp("receivedAt"),
                    rs.getTimestamp("completedAt")),
            orderId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  private List<OrderView.Refund> findRefunds(long orderId) {
    return queries.query(
        "SELECT id AS refundId,source,status,amount,attempt_no AS attemptNo,reconciliation_attempts AS reconciliationAttempts FROM refunds WHERE order_id=? ORDER BY attempt_no",
        (rs, rowNumber) ->
            new OrderView.Refund(
                rs.getLong("refundId"),
                rs.getString("source"),
                rs.getString("status"),
                rs.getBigDecimal("amount"),
                rs.getInt("attemptNo"),
                rs.getInt("reconciliationAttempts")),
        orderId);
  }

  private static Boolean nullableBoolean(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
    boolean value = rs.getBoolean(column);
    return rs.wasNull() ? null : value;
  }

  public record Summary(
      long orderId,
      String orderNumber,
      String source,
      String status,
      BigDecimal paymentAmount,
      Timestamp createdAt,
      Timestamp paidAt) {}

}
