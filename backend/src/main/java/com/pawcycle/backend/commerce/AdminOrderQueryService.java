package com.pawcycle.backend.commerce;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class AdminOrderQueryService {
  private final JdbcTemplate jdbc;

  AdminOrderQueryService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  List<Map<String, Object>> list() {
    return jdbc.queryForList(
        "SELECT id AS orderId,order_number AS orderNumber,member_id AS"
            + " memberId,status,payment_amount AS paymentAmount,created_at AS createdAt FROM orders"
            + " ORDER BY id DESC");
  }

  Map<String, Object> get(long id) {
    var rows =
        jdbc.queryForList(
            "SELECT id AS orderId,order_number AS orderNumber,member_id AS"
                + " memberId,status,payment_amount AS paymentAmount,created_at AS createdAt FROM"
                + " orders WHERE id=?",
            id);
    if (rows.isEmpty()) throw new CommerceException(404, "ORDER_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    return rows.getFirst();
  }
}
