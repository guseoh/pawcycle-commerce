package com.pawcycle.backend.commerce;

import java.util.List;
import java.util.Map;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import org.springframework.stereotype.Service;

@Service
public class AdminOrderQueryService {
  private final NativeQueryExecutor jdbc;

  public AdminOrderQueryService(NativeQueryExecutor jdbc) {
    this.jdbc = jdbc;
  }

  public List<CommerceRowResponse> list() {
    return CommerceRowResponse.from(
        jdbc.queryForList(
            "SELECT id AS orderId,order_number AS orderNumber,member_id AS"
                + " memberId,status,payment_amount AS paymentAmount,created_at AS createdAt FROM orders"
                + " ORDER BY id DESC"));
  }

  public CommercePayload get(long id) {
    var rows =
        jdbc.queryForList(
            "SELECT id AS orderId,order_number AS orderNumber,member_id AS"
                + " memberId,status,payment_amount AS paymentAmount,created_at AS createdAt FROM"
                + " orders WHERE id=?",
            id);
    if (rows.isEmpty()) throw new CommerceException(404, "ORDER_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    return CommercePayload.from(rows.getFirst());
  }
}
