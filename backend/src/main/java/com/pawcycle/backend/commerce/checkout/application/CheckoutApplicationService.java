package com.pawcycle.backend.commerce.checkout.application;

import com.pawcycle.backend.commerce.CommerceException;
import com.pawcycle.backend.commerce.CommercePayload;
import com.pawcycle.backend.commerce.InventoryService;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CheckoutApplicationService {
  private final NativeQueryExecutor jdbc;
  private final TransactionTemplate transaction;
  private final InventoryService inventoryService;
  private final Clock clock;

  public CheckoutApplicationService(
      NativeQueryExecutor jdbc,
      org.springframework.transaction.PlatformTransactionManager transactionManager,
      InventoryService inventoryService,
      Clock clock) {
    this.jdbc = jdbc;
    this.transaction = new TransactionTemplate(transactionManager);
    this.inventoryService = inventoryService;
    this.clock = clock;
  }

  public CommercePayload checkout(
      long memberId, String idempotencyKey, long addressId, Long memberCouponId) {
    return checkout(memberId, idempotencyKey, addressId, memberCouponId, null);
  }

  public CommercePayload checkout(
      long memberId,
      String idempotencyKey,
      long addressId,
      Long memberCouponId,
      Long requestedCartVersion) {
    if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
      throw new CommerceException(400, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key가 필요합니다.");
    }
    return CommercePayload.from(
        transaction.execute(
            status -> {
              lockMember(memberId);
              CartLock cart = lockCart(memberId);
              if (requestedCartVersion != null && requestedCartVersion.longValue() != cart.version()) {
                throw new CommerceException(409, "CART_CHANGED", "장바구니가 변경되었습니다.");
              }
              String fingerprint = checkoutFingerprint(addressId, memberCouponId, cart.version());
              Map<String, Object> replay =
                  one(
                      """
                      SELECT result.request_fingerprint AS requestFingerprint,orders.id AS orderId,orders.order_number AS orderNumber,payment.id AS paymentId,payment.provider_order_id AS providerOrderId,orders.payment_amount AS amount
                      FROM checkout_idempotency_results result JOIN orders ON orders.id=result.order_id JOIN payments payment ON payment.id=result.payment_id
                      WHERE result.member_id=? AND result.idempotency_key=? FOR UPDATE
                      """,
                      memberId,
                      idempotencyKey);
              if (replay != null) {
                if (replay.get("requestFingerprint") == null
                    || !fingerprint.equals(replay.get("requestFingerprint"))) {
                  throw new CommerceException(
                      409, "IDEMPOTENCY_KEY_CONFLICT", "Idempotency-Key가 다른 요청에 사용되었습니다.");
                }
                return checkoutResponse(replay);
              }

              Map<String, Object> address =
                  one(
                      "SELECT id,recipient_name,recipient_phone,postal_code,address_line1,address_line2"
                          + " FROM member_addresses WHERE id=? AND member_id=?",
                      addressId,
                      memberId);
              if (address == null) notFound("ADDRESS_NOT_FOUND");

              List<Map<String, Object>> items =
                  jdbc.queryForList(
                      """
                      SELECT item.sku_id,item.quantity,sku.sku_code,sku.name AS sku_name,sku.price,product.name AS product_name
                      FROM carts cart JOIN cart_items item ON item.cart_id=cart.id JOIN skus sku ON sku.id=item.sku_id
                      JOIN products product ON product.id=sku.product_id WHERE cart.id=? FOR UPDATE
                      """,
                      cart.id());
              if (items.isEmpty()) throw new CommerceException(409, "CART_EMPTY", "장바구니가 비어 있습니다.");

              BigDecimal original = BigDecimal.ZERO;
              for (Map<String, Object> item : items) {
                requirePurchasableSku(number(item, "sku_id"));
                original =
                    original.add(
                        decimal(item, "price").multiply(BigDecimal.valueOf(number(item, "quantity"))));
              }
              BigDecimal discount =
                  memberCouponId == null
                      ? BigDecimal.ZERO
                      : reserveCoupon(memberId, memberCouponId, original);
              BigDecimal amount = original.subtract(discount);
              if (amount.compareTo(BigDecimal.valueOf(100)) < 0) {
                throw new CommerceException(409, "PAYMENT_AMOUNT_TOO_LOW", "결제 금액은 100원 이상이어야 합니다.");
              }

              String orderNumber = "O-" + UUID.randomUUID();
              jdbc.update(
                  "INSERT INTO"
                      + " orders(order_number,member_id,source,status,original_amount,discount_amount,shipping_fee,payment_amount,recipient_name,recipient_phone,postal_code,address_line1,address_line2,created_at)"
                      + " VALUES (?,?,'ONE_TIME','PAYMENT_PENDING',?,?,?,?,?,?,?,?,?,?)",
                  orderNumber,
                  memberId,
                  original,
                  discount,
                  BigDecimal.ZERO,
                  amount,
                  address.get("recipient_name"),
                  address.get("recipient_phone"),
                  address.get("postal_code"),
                  address.get("address_line1"),
                  address.get("address_line2"),
                  now());
              long orderId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

              String providerOrderId = "TOSS-" + UUID.randomUUID();
              String paymentIdempotency = "pay-" + UUID.randomUUID();
              Timestamp expiresAt = Timestamp.from(clock.instant().plus(30, ChronoUnit.MINUTES));
              jdbc.update(
                  "INSERT INTO"
                      + " payments(order_id,type,provider,status,amount,provider_order_id,idempotency_key,attempt_no,requested_at,expires_at,created_at)"
                      + " VALUES (?,'NORMAL','TOSS','READY',?,?,?,?,?,?,?)",
                  orderId,
                  amount,
                  providerOrderId,
                  paymentIdempotency,
                  1,
                  now(),
                  expiresAt,
                  now());
              long paymentId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

              for (Map<String, Object> item : items) {
                long skuId = number(item, "sku_id");
                int quantity = (int) number(item, "quantity");
                inventoryService.reserve(skuId, quantity, paymentId);
                BigDecimal price = decimal(item, "price");
                jdbc.update(
                    "INSERT INTO"
                        + " order_items(order_id,sku_id,snapshot_quality,sku_code_snapshot,product_name_snapshot,sku_name_snapshot,unit_price,quantity,line_amount)"
                        + " VALUES (?,?,'FULL',?,?,?,?,?,?)",
                    orderId,
                    skuId,
                    item.get("sku_code"),
                    item.get("product_name"),
                    item.get("sku_name"),
                    price,
                    quantity,
                    price.multiply(BigDecimal.valueOf(quantity)));
              }
              if (memberCouponId != null) {
                jdbc.update(
                    "UPDATE member_coupons SET status='RESERVED',reserved_order_id=? WHERE id=? AND"
                        + " member_id=? AND status='AVAILABLE'",
                    orderId,
                    memberCouponId,
                    memberId);
              }
              jdbc.update(
                  "INSERT INTO"
                      + " checkout_idempotency_results(member_id,idempotency_key,order_id,payment_id,request_fingerprint,created_at)"
                      + " VALUES (?,?,?,?,?,?)",
                  memberId,
                  idempotencyKey,
                  orderId,
                  paymentId,
                  fingerprint,
                  now());
              Map<String, Object> result =
                  new LinkedHashMap<>(
                      Map.of(
                          "orderId", orderId,
                          "orderNumber", orderNumber,
                          "paymentId", paymentId,
                          "providerOrderId", providerOrderId,
                          "orderName", orderName(items),
                          "amount", amount));
              result.put("pricing", pricing(original, discount, BigDecimal.ZERO, amount));
              return result;
            }));
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
            "SELECT original_amount AS originalAmount,discount_amount AS discountAmount,shipping_fee AS"
                + " shippingFee,payment_amount AS paymentAmount FROM orders WHERE id=?",
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

  private BigDecimal reserveCoupon(long memberId, long id, BigDecimal original) {
    Map<String, Object> coupon =
        one(
            "SELECT coupon.discount_type,coupon.discount_value,coupon.minimum_order_amount,coupon.maximum_discount_amount"
                + " FROM member_coupons member_coupon JOIN coupons coupon ON coupon.id=member_coupon.coupon_id WHERE"
                + " member_coupon.id=? AND member_coupon.member_id=? AND member_coupon.status='AVAILABLE' AND"
                + " coupon.active=true AND coupon.valid_from<=? AND coupon.valid_until>? FOR UPDATE",
            id,
            memberId,
            now(),
            now());
    if (coupon == null) throw new CommerceException(409, "COUPON_UNAVAILABLE", "사용할 수 없는 쿠폰입니다.");
    if (original.compareTo(decimal(coupon, "minimum_order_amount")) < 0) {
      throw new CommerceException(409, "COUPON_MINIMUM_ORDER", "최소 주문 금액을 충족하지 않습니다.");
    }
    BigDecimal discount =
        "PERCENTAGE".equals(coupon.get("discount_type"))
            ? original
                .multiply(decimal(coupon, "discount_value"))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
            : decimal(coupon, "discount_value");
    if (coupon.get("maximum_discount_amount") != null)
      discount = discount.min(decimal(coupon, "maximum_discount_amount"));
    return discount.min(original);
  }

  private void lockMember(long memberId) {
    Long locked =
        jdbc.query(
            "SELECT id FROM members WHERE id=? FOR UPDATE",
            rs -> rs.next() ? rs.getLong(1) : null,
            memberId);
    if (locked == null) notFound("MEMBER_NOT_FOUND");
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

  private void requirePurchasableSku(long skuId) {
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM skus sku JOIN products product ON product.id=sku.product_id JOIN"
                + " categories category ON category.id=product.category_id WHERE sku.id=? AND"
                + " sku.status='ACTIVE' AND product.display_status='PUBLIC' AND category.active=true",
            Integer.class,
            skuId)
        != 1) {
      throw new CommerceException(409, "SKU_NOT_PURCHASABLE", "구매할 수 없는 SKU입니다.");
    }
  }

  private Map<String, Object> one(String sql, Object... args) {
    List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
    return rows.isEmpty() ? null : new LinkedHashMap<>(rows.getFirst());
  }

  private long incrementCartVersion(long cartId) {
    jdbc.update("UPDATE carts SET version=version+1,updated_at=? WHERE id=?", now(), cartId);
    return jdbc.queryForObject("SELECT version FROM carts WHERE id=?", Long.class, cartId);
  }

  private static String checkoutFingerprint(long addressId, Long memberCouponId, long cartVersion) {
    String payload = addressId + "|" + (memberCouponId == null ? "none" : memberCouponId) + "|" + cartVersion;
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

  private static void notFound(String code) {
    throw new CommerceException(404, code, "요청한 리소스를 찾을 수 없습니다.");
  }

  private record CartLock(long id, long version) {}
}
