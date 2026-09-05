package com.pawcycle.backend.commerce.order.persistence;

import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class AdminOrderPersistenceAdapter {
  private final NativeQueryExecutor queries;

  public AdminOrderPersistenceAdapter(NativeQueryExecutor queries) {
    this.queries = queries;
  }

  public List<AdminOrderView> findAll() {
    return queries.query(
        "SELECT id AS orderId,order_number AS orderNumber,member_id AS memberId,status,payment_amount AS paymentAmount,created_at AS createdAt FROM orders ORDER BY id DESC",
        (rs, rowNumber) ->
            new AdminOrderView(
                rs.getLong("orderId"),
                rs.getString("orderNumber"),
                rs.getLong("memberId"),
                rs.getString("status"),
                rs.getBigDecimal("paymentAmount"),
                rs.getTimestamp("createdAt")));
  }

  public AdminOrderView find(long orderId) {
    return queries
        .query(
            "SELECT id AS orderId,order_number AS orderNumber,member_id AS memberId,status,payment_amount AS paymentAmount,created_at AS createdAt FROM orders WHERE id=?",
            (rs, rowNumber) ->
                new AdminOrderView(
                    rs.getLong("orderId"),
                    rs.getString("orderNumber"),
                    rs.getLong("memberId"),
                    rs.getString("status"),
                    rs.getBigDecimal("paymentAmount"),
                    rs.getTimestamp("createdAt")),
            orderId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public record AdminOrderView(long orderId, String orderNumber, long memberId, String status, BigDecimal paymentAmount, Timestamp createdAt) {}
}
