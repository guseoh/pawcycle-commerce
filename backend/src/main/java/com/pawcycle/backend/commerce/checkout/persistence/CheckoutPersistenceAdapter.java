package com.pawcycle.backend.commerce.checkout.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class CheckoutPersistenceAdapter {
  private final JdbcTemplate queries;
  private final Clock clock;

  public CheckoutPersistenceAdapter(JdbcTemplate queries, Clock clock) {
    this.queries = queries;
    this.clock = clock;
  }

  public CheckoutReplay findReplay(long memberId, String idempotencyKey) {
    return queries
        .query(
            """
            SELECT result.request_fingerprint AS requestFingerprint,
                   result.request_cart_version AS requestCartVersion,
                   orders.id AS orderId,orders.order_number AS orderNumber,
                   payment.id AS paymentId,payment.provider_order_id AS providerOrderId,
                   orders.payment_amount AS amount
            FROM checkout_idempotency_results result
            JOIN orders ON orders.id=result.order_id
            JOIN payments payment ON payment.id=result.payment_id
            WHERE result.member_id=? AND result.idempotency_key=? FOR UPDATE
            """,
            (rs, rowNumber) -> {
              long requestCartVersion = rs.getLong("requestCartVersion");
              return new CheckoutReplay(
                  rs.getString("requestFingerprint"),
                  rs.wasNull() ? null : requestCartVersion,
                  rs.getLong("orderId"),
                  rs.getString("orderNumber"),
                  rs.getLong("paymentId"),
                  rs.getString("providerOrderId"),
                  rs.getBigDecimal("amount"));
            },
            memberId,
            idempotencyKey)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public CheckoutAddress findAddress(long memberId, long addressId) {
    return queries
        .query(
            "SELECT recipient_name,recipient_phone,postal_code,address_line1,address_line2 FROM member_addresses WHERE id=? AND member_id=?",
            (rs, rowNumber) ->
                new CheckoutAddress(
                    rs.getString("recipient_name"),
                    rs.getString("recipient_phone"),
                    rs.getString("postal_code"),
                    rs.getString("address_line1"),
                    rs.getString("address_line2")),
            addressId,
            memberId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public List<CheckoutCartItem> findCartItems(long cartId) {
    return queries.query(
        "SELECT item.sku_id,item.quantity,sku.sku_code,sku.name AS sku_name,sku.price,product.name AS product_name FROM carts cart JOIN cart_items item ON item.cart_id=cart.id JOIN skus sku ON sku.id=item.sku_id JOIN products product ON product.id=sku.product_id WHERE cart.id=? FOR UPDATE",
        (rs, rowNumber) ->
            new CheckoutCartItem(
                rs.getLong("sku_id"),
                rs.getInt("quantity"),
                rs.getString("sku_code"),
                rs.getString("sku_name"),
                rs.getBigDecimal("price"),
                rs.getString("product_name")),
        cartId);
  }

  public boolean isPurchasable(long skuId) {
    List<Integer> matches =
        queries.queryForList(
            "SELECT COUNT(*) FROM skus sku JOIN products product ON product.id=sku.product_id JOIN categories category ON category.id=product.category_id WHERE sku.id=? AND sku.status='ACTIVE' AND product.display_status='PUBLIC' AND category.active=true",
            Integer.class,
            skuId);
    return !matches.isEmpty() && matches.getFirst() == 1;
  }

  public CouponRule findCouponRule(long memberId, long memberCouponId) {
    return queries
        .query(
            "SELECT coupon.discount_type,coupon.discount_value,coupon.minimum_order_amount,coupon.maximum_discount_amount FROM member_coupons member_coupon JOIN coupons coupon ON coupon.id=member_coupon.coupon_id WHERE member_coupon.id=? AND member_coupon.member_id=? AND member_coupon.status='AVAILABLE' AND coupon.active=true AND coupon.valid_from<=? AND coupon.valid_until>? FOR UPDATE",
            (rs, rowNumber) ->
                new CouponRule(
                    rs.getBigDecimal("minimum_order_amount"),
                    rs.getBigDecimal("discount_value"),
                    rs.getBigDecimal("maximum_discount_amount"),
                    rs.getString("discount_type")),
            memberCouponId,
            memberId,
            now(),
            now())
        .stream()
        .findFirst()
        .orElse(null);
  }

  public long createOrder(long memberId, String orderNumber, BigDecimal original, BigDecimal discount,
      BigDecimal amount, CheckoutAddress address) {
    queries.update(
        "INSERT INTO orders(order_number,member_id,source,status,original_amount,discount_amount,shipping_fee,payment_amount,recipient_name,recipient_phone,postal_code,address_line1,address_line2,created_at) VALUES (?,?,'ONE_TIME','PAYMENT_PENDING',?,?,?,?,?,?,?,?,?,?)",
        orderNumber,
        memberId,
        original,
        discount,
        BigDecimal.ZERO,
        amount,
        address.recipientName(),
        address.recipientPhone(),
        address.postalCode(),
        address.addressLine1(),
        address.addressLine2(),
        now());
    return queries.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }

  public long createPayment(long orderId, BigDecimal amount) {
    queries.update(
        "INSERT INTO payments(order_id,type,provider,status,amount,provider_order_id,idempotency_key,attempt_no,requested_at,expires_at,created_at) VALUES (?,'NORMAL','TOSS','READY',?,?,?,?,?,?,?)",
        orderId,
        amount,
        "TOSS-" + UUID.randomUUID(),
        "pay-" + UUID.randomUUID(),
        1,
        now(),
        Timestamp.from(clock.instant().plus(30, ChronoUnit.MINUTES)),
        now());
    return queries.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }

  public String providerOrderId(long paymentId) {
    return queries.queryForObject(
        "SELECT provider_order_id FROM payments WHERE id=?", String.class, paymentId);
  }

  public void createOrderItem(long orderId, CheckoutCartItem item) {
    queries.update(
        "INSERT INTO order_items(order_id,sku_id,snapshot_quality,sku_code_snapshot,product_name_snapshot,sku_name_snapshot,unit_price,quantity,line_amount) VALUES (?,?,'FULL',?,?,?,?,?,?)",
        orderId,
        item.skuId(),
        item.skuCode(),
        item.productName(),
        item.skuName(),
        item.price(),
        item.quantity(),
        item.price().multiply(BigDecimal.valueOf(item.quantity())));
  }

  public void reserveCoupon(long orderId, long memberCouponId, long memberId) {
    queries.update(
        "UPDATE member_coupons SET status='RESERVED',reserved_order_id=? WHERE id=? AND member_id=? AND status='AVAILABLE'",
        orderId,
        memberCouponId,
        memberId);
  }

  public void saveIdempotency(
      long memberId,
      String idempotencyKey,
      long orderId,
      long paymentId,
      String fingerprint) {
    queries.update(
        "INSERT INTO checkout_idempotency_results(member_id,idempotency_key,order_id,payment_id,request_fingerprint,request_cart_version,created_at) VALUES (?,?,?,?,?,?,?)",
        memberId,
        idempotencyKey,
        orderId,
        paymentId,
        fingerprint,
        null,
        now());
  }

  public void saveCartVersion(long memberId, String idempotencyKey, long cartVersion) {
    int updated =
        queries.update(
            "UPDATE checkout_idempotency_results SET request_cart_version=? WHERE member_id=? AND idempotency_key=? AND request_cart_version IS NULL",
            cartVersion,
            memberId,
            idempotencyKey);
    if (updated != 1) throw new IllegalStateException("Checkout 요청 버전을 저장할 수 없습니다.");
  }

  public CheckoutOrderPricing findOrderPricing(long orderId) {
    return queries
        .query(
            "SELECT original_amount,discount_amount,shipping_fee,payment_amount FROM orders WHERE id=?",
            (rs, rowNumber) ->
                new CheckoutOrderPricing(
                    rs.getBigDecimal("original_amount"),
                    rs.getBigDecimal("discount_amount"),
                    rs.getBigDecimal("shipping_fee"),
                    rs.getBigDecimal("payment_amount")),
            orderId)
        .stream()
        .findFirst()
        .orElseThrow();
  }

  public List<String> findProductNames(long orderId) {
    return queries.queryForList(
        "SELECT product_name_snapshot FROM order_items WHERE order_id=? ORDER BY id",
        String.class,
        orderId);
  }

  private Timestamp now() {
    return Timestamp.from(clock.instant());
  }
}
