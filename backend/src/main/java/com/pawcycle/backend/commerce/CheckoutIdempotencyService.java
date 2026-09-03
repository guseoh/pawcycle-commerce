package com.pawcycle.backend.commerce;

import com.pawcycle.backend.commerce.checkout.application.CheckoutApplicationService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutIdempotencyService {
  private final NativeQueryExecutor jdbc;
  private final CheckoutApplicationService checkoutService;

  public CheckoutIdempotencyService(NativeQueryExecutor jdbc, CheckoutApplicationService checkoutService) {
    this.jdbc = jdbc;
    this.checkoutService = checkoutService;
  }

  @Transactional
  public CommercePayload checkout(
      long memberId,
      String idempotencyKey,
      long addressId,
      Long memberCouponId,
      Long requestedCartVersion) {
    if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
      throw new CommerceException(400, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key가 필요합니다.");
    }

    lockMember(memberId);
    Map<String, Object> replay =
        one(
            """
            SELECT result.request_fingerprint AS requestFingerprint,
                   result.request_cart_version AS requestCartVersion,
                   orders.id AS orderId,
                   orders.order_number AS orderNumber,
                   payment.id AS paymentId,
                   payment.provider_order_id AS providerOrderId,
                   orders.payment_amount AS amount
            FROM checkout_idempotency_results result
            JOIN orders ON orders.id=result.order_id
            JOIN payments payment ON payment.id=result.payment_id
            WHERE result.member_id=? AND result.idempotency_key=?
            FOR UPDATE
            """,
            memberId,
            idempotencyKey);
    if (replay != null) {
      verifyReplayIdentity(replay, addressId, memberCouponId, requestedCartVersion);
      return CommercePayload.from(checkoutResponse(replay));
    }

    long currentCartVersion = lockCurrentCartVersion(memberId);
    long requestCartVersion =
        requestedCartVersion == null ? currentCartVersion : requestedCartVersion;
    CommercePayload result =
        checkoutService.checkout(
            memberId, idempotencyKey, addressId, memberCouponId, requestCartVersion);
    int updated =
        jdbc.update(
            """
            UPDATE checkout_idempotency_results
            SET request_cart_version=?
            WHERE member_id=? AND idempotency_key=? AND request_cart_version IS NULL
            """,
            requestCartVersion,
            memberId,
            idempotencyKey);
    if (updated != 1) {
      throw new IllegalStateException("Checkout 요청 버전을 저장할 수 없습니다.");
    }
    return result;
  }

  private void verifyReplayIdentity(
      Map<String, Object> replay, long addressId, Long memberCouponId, Long requestedCartVersion) {
    Object storedFingerprint = replay.get("requestFingerprint");
    Object storedCartVersion = replay.get("requestCartVersion");
    if (storedFingerprint == null || storedCartVersion == null) {
      conflict();
    }
    long originalCartVersion = ((Number) storedCartVersion).longValue();
    long fingerprintCartVersion =
        requestedCartVersion == null ? originalCartVersion : requestedCartVersion;
    String fingerprint = checkoutFingerprint(addressId, memberCouponId, fingerprintCartVersion);
    if (!fingerprint.equals(storedFingerprint)) {
      conflict();
    }
  }

  private long lockCurrentCartVersion(long memberId) {
    Map<String, Object> cart =
        one("SELECT version FROM carts WHERE member_id=? FOR UPDATE", memberId);
    return cart == null ? 0L : number(cart, "version");
  }

  private void lockMember(long memberId) {
    Long locked =
        jdbc.query(
            "SELECT id FROM members WHERE id=? FOR UPDATE",
            rs -> rs.next() ? rs.getLong(1) : null,
            memberId);
    if (locked == null) {
      throw new CommerceException(404, "MEMBER_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    }
  }

  private Map<String, Object> checkoutResponse(Map<String, Object> row) {
    long orderId = number(row, "orderId");
    List<Map<String, Object>> items =
        jdbc.queryForList(
            "SELECT product_name_snapshot AS product_name FROM order_items WHERE order_id=? ORDER"
                + " BY id",
            orderId);
    Map<String, Object> result =
        new LinkedHashMap<>(
            Map.of(
                "orderId", row.get("orderId"),
                "orderNumber", row.get("orderNumber"),
                "paymentId", row.get("paymentId"),
                "providerOrderId", row.get("providerOrderId"),
                "orderName", orderName(items),
                "amount", row.get("amount")));
    Map<String, Object> order =
        one(
            """
            SELECT original_amount AS originalAmount,discount_amount AS discountAmount,
                   shipping_fee AS shippingFee,payment_amount AS paymentAmount
            FROM orders WHERE id=?
            """,
            orderId);
    result.put(
        "pricing",
        pricing(
            decimal(order, "originalAmount"),
            decimal(order, "discountAmount"),
            decimal(order, "shippingFee"),
            decimal(order, "paymentAmount")));
    return result;
  }

  private Map<String, Object> one(String sql, Object... args) {
    List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
    return rows.isEmpty() ? null : new LinkedHashMap<>(rows.getFirst());
  }

  private static long number(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).longValue();
  }

  private static BigDecimal decimal(Map<String, Object> row, String key) {
    Object value = row.get(key);
    return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
  }

  private static String checkoutFingerprint(long addressId, Long memberCouponId, long cartVersion) {
    String payload =
        addressId + "|" + (memberCouponId == null ? "none" : memberCouponId) + "|" + cartVersion;
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder(64);
      for (byte value : digest) result.append(String.format("%02x", value));
      return result.toString();
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
    }
  }

  private static String orderName(List<Map<String, Object>> items) {
    return items.getFirst().get("product_name")
        + (items.size() > 1 ? " 외 " + (items.size() - 1) + "건" : "");
  }

  private static Map<String, Object> pricing(
      BigDecimal original, BigDecimal discount, BigDecimal shipping, BigDecimal payment) {
    return Map.of(
        "originalAmount", original,
        "subtotalAmount", original.subtract(discount),
        "discountAmount", discount,
        "shippingFee", shipping,
        "finalAmount", payment,
        "paymentAmount", payment);
  }

  private static void conflict() {
    throw new CommerceException(
        409, "IDEMPOTENCY_KEY_CONFLICT", "Idempotency-Key가 다른 요청에 사용되었습니다.");
  }
}
