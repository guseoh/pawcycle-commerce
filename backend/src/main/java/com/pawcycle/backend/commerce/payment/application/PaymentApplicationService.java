package com.pawcycle.backend.commerce.payment.application;

import com.pawcycle.backend.commerce.CommerceException;
import com.pawcycle.backend.commerce.CommercePayload;
import com.pawcycle.backend.commerce.DeliveryService;
import com.pawcycle.backend.commerce.InventoryService;
import com.pawcycle.backend.commerce.MembershipEvaluationService;
import com.pawcycle.backend.commerce.NotificationService;
import com.pawcycle.backend.commerce.TossPaymentAdapter;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentApplicationService {
  private final NativeQueryExecutor jdbc;
  private final TransactionTemplate transaction;
  private final TossPaymentAdapter tossPaymentAdapter;
  private final DeliveryService deliveryService;
  private final NotificationService notificationService;
  private final InventoryService inventoryService;
  private final MembershipEvaluationService membershipEvaluation;
  private final Clock clock;

  public PaymentApplicationService(
      NativeQueryExecutor jdbc,
      org.springframework.transaction.PlatformTransactionManager transactionManager,
      TossPaymentAdapter tossPaymentAdapter,
      DeliveryService deliveryService,
      NotificationService notificationService,
      InventoryService inventoryService,
      MembershipEvaluationService membershipEvaluation,
      Clock clock) {
    this.jdbc = jdbc;
    this.transaction = new TransactionTemplate(transactionManager);
    this.tossPaymentAdapter = tossPaymentAdapter;
    this.deliveryService = deliveryService;
    this.notificationService = notificationService;
    this.inventoryService = inventoryService;
    this.membershipEvaluation = membershipEvaluation;
    this.clock = clock;
  }

  public CommercePayload confirm(
      long memberId, String paymentKey, String providerOrderId, BigDecimal amount) {
    if (!tossPaymentAdapter.isConfigured()) {
      throw new CommerceException(
          503, "PAYMENT_PROVIDER_UNAVAILABLE", "Toss 결제 Provider가 현재 환경에 구성되지 않았습니다.");
    }
    Map<String, Object> work =
        transaction.execute(
            status -> {
              Map<String, Object> payment =
                  one(
                      """
                      SELECT payment.id,payment.order_id,payment.amount,payment.status,payment.payment_key,orders.member_id,orders.status AS order_status
                      FROM payments payment JOIN orders ON orders.id=payment.order_id WHERE payment.provider_order_id=? FOR UPDATE
                      """,
                      providerOrderId);
              if (payment == null) notFound("PAYMENT_NOT_FOUND");
              if (number(payment, "member_id") != memberId) {
                throw new CommerceException(403, "PAYMENT_FORBIDDEN", "결제 소유자가 아닙니다.");
              }
              if ("SUCCEEDED".equals(payment.get("status"))
                  && "PAID".equals(payment.get("order_status"))
                  && decimal(payment, "amount").compareTo(amount) == 0
                  && paymentKey.equals(payment.get("payment_key"))) {
                payment.put("replay", true);
                return payment;
              }
              if (!"READY".equals(payment.get("status"))
                  || !"PAYMENT_PENDING".equals(payment.get("order_status"))
                  || decimal(payment, "amount").compareTo(amount) != 0) {
                throw new CommerceException(
                    409, "PAYMENT_CONFIRM_CONFLICT", "결제 확인 상태가 올바르지 않습니다.");
              }
              jdbc.update(
                  "UPDATE payments SET status='PROCESSING',payment_key=?,provider_status='REQUESTED'"
                      + " WHERE id=?",
                  paymentKey,
                  number(payment, "id"));
              return payment;
            });

    if (Boolean.TRUE.equals(work.get("replay"))) {
      return CommercePayload.from(
          Map.of(
              "paymentId",
              number(work, "id"),
              "orderId",
              number(work, "order_id"),
              "status",
              "SUCCEEDED"));
    }

    try {
      TossPaymentAdapter.ConfirmResult result =
          tossPaymentAdapter.confirm(paymentKey, providerOrderId, amount);
      return CommercePayload.from(
          transaction.execute(
              status -> finalizePayment(number(work, "id"), result.status(), paymentKey)));
    } catch (RuntimeException exception) {
      return CommercePayload.from(
          transaction.execute(status -> markProviderUnknown(number(work, "id"))));
    }
  }

  private Map<String, Object> finalizePayment(long paymentId, String result, String paymentKey) {
    Map<String, Object> payment =
        one("SELECT id,order_id,status FROM payments WHERE id=? FOR UPDATE", paymentId);
    if (payment == null) notFound("PAYMENT_NOT_FOUND");
    if (!"PROCESSING".equals(payment.get("status"))) {
      return Map.of(
          "paymentId", paymentId,
          "orderId", payment.get("order_id"),
          "status", payment.get("status"));
    }
    long orderId = number(payment, "order_id");
    if ("UNKNOWN".equals(result)) {
      jdbc.update(
          "UPDATE payments SET status='UNKNOWN',provider_status='UNKNOWN' WHERE id=?", paymentId);
      return Map.of("paymentId", paymentId, "orderId", orderId, "status", "UNKNOWN");
    }
    List<Map<String, Object>> items =
        jdbc.queryForList("SELECT sku_id,quantity FROM order_items WHERE order_id=?", orderId);
    if ("SUCCEEDED".equals(result)) {
      for (Map<String, Object> item : items) {
        inventoryService.deduct(number(item, "sku_id"), (int) number(item, "quantity"), paymentId);
      }
      jdbc.update(
          "UPDATE payments SET status='SUCCEEDED',provider_status='DONE',payment_key=?,approved_at=?"
              + " WHERE id=?",
          paymentKey,
          now(),
          paymentId);
      jdbc.update("UPDATE orders SET status='PAID',paid_at=? WHERE id=?", now(), orderId);
      deliveryService.createPreparing(orderId);
      jdbc.update(
          "UPDATE member_coupons SET status='USED',used_at=? WHERE reserved_order_id=? AND"
              + " status='RESERVED'",
          now(),
          orderId);
      long memberId =
          jdbc.queryForObject("SELECT member_id FROM orders WHERE id=?", Long.class, orderId);
      notificationService.create(memberId, "ORDER_PAID", "ORDER", orderId);
      consumeCartForOrder(memberId, orderId);
      membershipEvaluation.evaluate(memberId);
      return Map.of("paymentId", paymentId, "orderId", orderId, "status", "SUCCEEDED");
    }
    for (Map<String, Object> item : items) {
      inventoryService.release(number(item, "sku_id"), (int) number(item, "quantity"), paymentId);
    }
    jdbc.update(
        "UPDATE payments SET status='FAILED',provider_status='ABORTED',failure_code='TOSS_REJECTED',"
            + "failed_at=? WHERE id=?",
        now(),
        paymentId);
    jdbc.update("UPDATE orders SET status='PAYMENT_FAILED' WHERE id=?", orderId);
    jdbc.update(
        "UPDATE member_coupons SET status='AVAILABLE',reserved_order_id=NULL WHERE"
            + " reserved_order_id=? AND status='RESERVED'",
        orderId);
    return Map.of("paymentId", paymentId, "orderId", orderId, "status", "FAILED");
  }

  private Map<String, Object> markProviderUnknown(long paymentId) {
    Map<String, Object> payment =
        one("SELECT order_id,status FROM payments WHERE id=? FOR UPDATE", paymentId);
    if (payment == null) notFound("PAYMENT_NOT_FOUND");
    if ("PROCESSING".equals(payment.get("status"))) {
      jdbc.update(
          "UPDATE payments SET status='UNKNOWN',provider_status='UNKNOWN',failure_code="
              + "'PROVIDER_RESULT_UNKNOWN' WHERE id=?",
          paymentId);
    }
    return Map.of("paymentId", paymentId, "orderId", payment.get("order_id"), "status", "UNKNOWN");
  }

  private void consumeCartForOrder(long memberId, long orderId) {
    CartLock cart = lockCart(memberId);
    boolean changed = false;
    for (Map<String, Object> item :
        jdbc.queryForList("SELECT sku_id,quantity FROM order_items WHERE order_id=?", orderId)) {
      long skuId = number(item, "sku_id");
      int purchased = (int) number(item, "quantity");
      Integer current =
          jdbc.query(
              "SELECT quantity FROM cart_items WHERE cart_id=? AND sku_id=? FOR UPDATE",
              rs -> rs.next() ? rs.getInt(1) : null,
              cart.id(),
              skuId);
      if (current == null) continue;
      if (current <= purchased) {
        jdbc.update("DELETE FROM cart_items WHERE cart_id=? AND sku_id=?", cart.id(), skuId);
      } else {
        jdbc.update(
            "UPDATE cart_items SET quantity=? WHERE cart_id=? AND sku_id=?",
            current - purchased,
            cart.id(),
            skuId);
      }
      changed = true;
    }
    if (changed) incrementCartVersion(cart.id());
  }

  private CartLock lockCart(long memberId) {
    Long cartId =
        jdbc.query(
            "SELECT id FROM carts WHERE member_id=? FOR UPDATE",
            rs -> rs.next() ? rs.getLong(1) : null,
            memberId);
    if (cartId == null) {
      jdbc.update(
          "INSERT INTO carts(member_id,created_at,updated_at) VALUES (?,?,?)",
          memberId,
          now(),
          now());
      cartId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
    Map<String, Object> row = one("SELECT id,version FROM carts WHERE id=? FOR UPDATE", cartId);
    return new CartLock(cartId, number(row, "version"));
  }

  private void incrementCartVersion(long cartId) {
    jdbc.update("UPDATE carts SET version=version+1,updated_at=? WHERE id=?", now(), cartId);
  }

  private Map<String, Object> one(String sql, Object... args) {
    List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
    return rows.isEmpty() ? null : new LinkedHashMap<>(rows.getFirst());
  }

  private Timestamp now() {
    return Timestamp.from(clock.instant());
  }

  private static long number(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).longValue();
  }

  private static BigDecimal decimal(Map<String, Object> row, String key) {
    Object value = row.get(key);
    return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
  }

  private static void notFound(String code) {
    throw new CommerceException(404, code, "요청한 리소스를 찾을 수 없습니다.");
  }

  private record CartLock(long id, long version) {}
}
