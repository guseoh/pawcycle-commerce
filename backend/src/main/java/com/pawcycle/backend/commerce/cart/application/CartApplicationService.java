package com.pawcycle.backend.commerce.cart.application;

import com.pawcycle.backend.commerce.CommerceException;
import com.pawcycle.backend.commerce.CommercePayload;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartApplicationService {
  private final NativeQueryExecutor jdbc;
  private final Clock clock;

  public CartApplicationService(NativeQueryExecutor jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public CommercePayload get(long memberId) {
    List<Map<String, Object>> items =
        jdbc.queryForList(
            """
            SELECT item.sku_id AS skuId,item.quantity,sku.sku_code AS skuCode,sku.name AS skuName,sku.price,sku.price AS unitPrice,
                   sku.price * item.quantity AS lineAmount,product.id AS productId,product.name AS productName,
                   inventory.available_quantity AS availableQuantity,
                   (sku.status='ACTIVE' AND product.display_status='PUBLIC' AND category.active=true AND inventory.available_quantity >= item.quantity) AS purchasable
            FROM carts cart JOIN cart_items item ON item.cart_id=cart.id
            JOIN skus sku ON sku.id=item.sku_id JOIN products product ON product.id=sku.product_id
            JOIN categories category ON category.id=product.category_id
            JOIN inventories inventory ON inventory.sku_id=sku.id
            WHERE cart.member_id=? ORDER BY item.sku_id\
            """,
            memberId);
    BigDecimal original =
        items.stream()
            .map(item -> decimal(item, "lineAmount"))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    Long version =
        jdbc.query(
            "SELECT version FROM carts WHERE member_id=?",
            rs -> rs.next() ? rs.getLong(1) : 0L,
            memberId);
    return CommercePayload.from(
        Map.of(
            "items", items,
            "version", version,
            "pricing", pricing(original, BigDecimal.ZERO, BigDecimal.ZERO, original)));
  }

  @Transactional
  public void add(long memberId, long skuId, int quantity) {
    lockMember(memberId);
    requirePurchasableSku(skuId);
    Long cartId = lockCartId(memberId);
    if (cartId == null) {
      jdbc.update(
          "INSERT INTO carts(member_id,created_at,updated_at) VALUES (?,?,?)",
          memberId,
          now(),
          now());
      cartId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
    int updated =
        jdbc.update(
            "UPDATE cart_items SET quantity=quantity+? WHERE cart_id=? AND sku_id=?",
            quantity,
            cartId,
            skuId);
    if (updated == 0) {
      jdbc.update(
          "INSERT INTO cart_items(cart_id,sku_id,quantity) VALUES (?,?,?)",
          cartId,
          skuId,
          quantity);
    }
    incrementVersion(cartId);
  }

  @Transactional
  public void update(long memberId, long skuId, int quantity) {
    Long cartId = lockCartId(memberId);
    if (cartId == null) notFound("CART_ITEM_NOT_FOUND");
    Integer current =
        jdbc.query(
            "SELECT quantity FROM cart_items WHERE cart_id=? AND sku_id=? FOR UPDATE",
            rs -> rs.next() ? rs.getInt(1) : null,
            cartId,
            skuId);
    if (current == null) notFound("CART_ITEM_NOT_FOUND");
    if (current != quantity) {
      jdbc.update(
          "UPDATE cart_items SET quantity=? WHERE cart_id=? AND sku_id=?",
          quantity,
          cartId,
          skuId);
      incrementVersion(cartId);
    }
  }

  @Transactional
  public void delete(long memberId, long skuId) {
    Long cartId = lockCartId(memberId);
    if (cartId == null) return;
    if (jdbc.update("DELETE FROM cart_items WHERE cart_id=? AND sku_id=?", cartId, skuId) == 1) {
      incrementVersion(cartId);
    }
  }

  private Long lockCartId(long memberId) {
    return jdbc.query(
        "SELECT id FROM carts WHERE member_id=? FOR UPDATE",
        rs -> rs.next() ? rs.getLong(1) : null,
        memberId);
  }

  private long incrementVersion(long cartId) {
    jdbc.update("UPDATE carts SET version=version+1,updated_at=? WHERE id=?", now(), cartId);
    return jdbc.queryForObject("SELECT version FROM carts WHERE id=?", Long.class, cartId);
  }

  private void lockMember(long memberId) {
    Long locked =
        jdbc.query(
            "SELECT id FROM members WHERE id=? FOR UPDATE",
            rs -> rs.next() ? rs.getLong(1) : null,
            memberId);
    if (locked == null) notFound("MEMBER_NOT_FOUND");
  }

  private void requirePurchasableSku(long skuId) {
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM skus sku JOIN products product ON product.id=sku.product_id JOIN"
                + " categories category ON category.id=product.category_id WHERE sku.id=? AND"
                + " sku.status='ACTIVE' AND product.display_status='PUBLIC' AND"
                + " category.active=true",
            Integer.class,
            skuId)
        != 1) {
      throw new CommerceException(409, "SKU_NOT_PURCHASABLE", "구매할 수 없는 SKU입니다.");
    }
  }

  private Timestamp now() {
    return Timestamp.from(clock.instant());
  }

  private static BigDecimal decimal(Map<String, Object> row, String key) {
    Object value = row.get(key);
    return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
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
}
