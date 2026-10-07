package com.pawcycle.backend.commerce.order.persistence;

import com.pawcycle.backend.commerce.common.error.CommerceException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class QuickReorderPersistenceAdapter {
  private final JdbcTemplate queries;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public QuickReorderPersistenceAdapter(JdbcTemplate queries, ObjectMapper objectMapper, Clock clock) {
    this.queries = queries;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public ReorderResult reorder(long memberId, long sourceOrderId, String idempotencyKey) {
    lockMember(memberId);
    Map<String, Object> existing =
        one(
            "SELECT source_order_id AS sourceOrderId,response_json AS responseJson FROM quick_reorder_idempotency_results WHERE member_id=? AND idempotency_key=? FOR UPDATE",
            memberId,
            idempotencyKey);
    if (existing != null) {
      if (number(existing, "sourceOrderId") != sourceOrderId) {
        throw new CommerceException(409, "IDEMPOTENCY_KEY_CONFLICT", "Idempotency-Key가 다른 주문에 사용되었습니다.");
      }
      return storedResponse((String) existing.get("responseJson"));
    }
    if (queries.queryForObject(
            "SELECT COUNT(*) FROM orders WHERE id=? AND member_id=?", Integer.class, sourceOrderId, memberId)
        != 1) {
      throw new CommerceException(404, "ORDER_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    }
    CartLock cart = lockCart(memberId);
    List<Map<String, Object>> sourceItems =
        queries.queryForList(
            "SELECT item.sku_id AS skuId,item.quantity,sku.status AS skuStatus,product.display_status AS productStatus,category.active AS categoryActive,inventory.available_quantity AS availableQuantity FROM order_items item JOIN skus sku ON sku.id=item.sku_id JOIN products product ON product.id=sku.product_id JOIN categories category ON category.id=product.category_id LEFT JOIN inventories inventory ON inventory.sku_id=sku.id WHERE item.order_id=? ORDER BY item.id FOR UPDATE",
            sourceOrderId);
    List<ReorderItem> addedItems = new ArrayList<>();
    List<SkippedItem> skippedItems = new ArrayList<>();
    for (Map<String, Object> sourceItem : sourceItems) {
      long skuId = number(sourceItem, "skuId");
      int quantity = (int) number(sourceItem, "quantity");
      String reason = reorderSkipReason(sourceItem, quantity);
      if (reason != null) {
        skippedItems.add(new SkippedItem(skuId, quantity, reason));
        continue;
      }
      int changed =
          queries.update(
              "UPDATE cart_items SET quantity=quantity+? WHERE cart_id=? AND sku_id=?",
              quantity,
              cart.id,
              skuId);
      if (changed == 0) {
        queries.update(
            "INSERT INTO cart_items(cart_id,sku_id,quantity) VALUES (?,?,?)", cart.id, skuId, quantity);
      }
      addedItems.add(new ReorderItem(skuId, quantity));
    }
    long resultCartVersion = cart.version;
    if (!addedItems.isEmpty()) resultCartVersion = incrementCartVersion(cart.id);
    ReorderResult result = new ReorderResult(addedItems, skippedItems, resultCartVersion);
    try {
      Map<String, Object> json = new LinkedHashMap<>();
      json.put("addedItems", addedItems);
      json.put("skippedItems", skippedItems);
      json.put("cartVersion", resultCartVersion);
      queries.update(
          "INSERT INTO quick_reorder_idempotency_results(member_id,idempotency_key,source_order_id,response_json,cart_version,created_at) VALUES (?,?,?,?,?,?)",
          memberId,
          idempotencyKey,
          sourceOrderId,
          objectMapper.writeValueAsString(json),
          resultCartVersion,
          now());
    } catch (Exception exception) {
      throw new IllegalStateException("Quick Reorder 결과를 저장할 수 없습니다.", exception);
    }
    return result;
  }

  private String reorderSkipReason(Map<String, Object> sourceItem, int quantity) {
    if (!"ACTIVE".equals(sourceItem.get("skuStatus"))
        || !"PUBLIC".equals(sourceItem.get("productStatus"))
        || !booleanValue(sourceItem.get("categoryActive"))) return "SKU_NOT_PURCHASABLE";
    Object available = sourceItem.get("availableQuantity");
    if (!(available instanceof Number number) || number.intValue() < quantity) return "OUT_OF_STOCK";
    return null;
  }

  private ReorderResult storedResponse(String responseJson) {
    try {
      var responseType =
          objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class);
      Map<String, Object> response = objectMapper.readValue(responseJson, responseType);
      List<ReorderItem> added =
          ((List<?>) response.get("addedItems"))
              .stream()
              .map(item -> item instanceof Map<?, ?> map
                  ? new ReorderItem(number(map, "skuId"), (int) number(map, "quantity"))
                  : new ReorderItem(0, 0))
              .toList();
      List<SkippedItem> skipped =
          ((List<?>) response.get("skippedItems"))
              .stream()
              .map(item -> {
                Map<?, ?> map = (Map<?, ?>) item;
                return new SkippedItem(number(map, "skuId"), (int) number(map, "quantity"), (String) map.get("reason"));
              })
              .toList();
      return new ReorderResult(added, skipped, number(response, "cartVersion"));
    } catch (Exception exception) {
      throw new CommerceException(409, "IDEMPOTENCY_KEY_CONFLICT", "저장된 Quick Reorder 결과를 읽을 수 없습니다.");
    }
  }

  private void lockMember(long memberId) {
    Long locked =
        queries.query("SELECT id FROM members WHERE id=? FOR UPDATE", rs -> rs.next() ? rs.getLong(1) : null, memberId);
    if (locked == null) throw new CommerceException(404, "MEMBER_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
  }

  private CartLock lockCart(long memberId) {
    Long cartId =
        queries.query("SELECT id FROM carts WHERE member_id=? FOR UPDATE", rs -> rs.next() ? rs.getLong(1) : null, memberId);
    if (cartId == null) {
      queries.update("INSERT INTO carts(member_id,created_at,updated_at) VALUES (?,?,?)", memberId, now(), now());
      cartId = queries.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
    Map<String, Object> row = one("SELECT id,version FROM carts WHERE id=? FOR UPDATE", cartId);
    return new CartLock(cartId, number(row, "version"));
  }

  private long incrementCartVersion(long cartId) {
    queries.update("UPDATE carts SET version=version+1,updated_at=? WHERE id=?", now(), cartId);
    return queries.queryForObject("SELECT version FROM carts WHERE id=?", Long.class, cartId);
  }

  private Map<String, Object> one(String sql, Object... args) {
    List<Map<String, Object>> rows = queries.queryForList(sql, args);
    return rows.isEmpty() ? null : new LinkedHashMap<>(rows.getFirst());
  }

  private Timestamp now() {
    return Timestamp.from(clock.instant());
  }

  private static boolean booleanValue(Object value) {
    return value instanceof Boolean booleanValue
        ? booleanValue
        : value instanceof Number number && number.intValue() != 0;
  }

  private static long number(Map<?, ?> row, String key) {
    return ((Number) row.get(key)).longValue();
  }

  public record ReorderResult(List<ReorderItem> addedItems, List<SkippedItem> skippedItems, long cartVersion) {}

  public record ReorderItem(long skuId, int quantity) {}

  public record SkippedItem(long skuId, int quantity, String reason) {}

  private record CartLock(long id, long version) {}
}
