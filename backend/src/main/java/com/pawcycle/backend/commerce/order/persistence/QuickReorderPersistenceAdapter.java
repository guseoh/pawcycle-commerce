package com.pawcycle.backend.commerce.order.persistence;

import com.pawcycle.backend.commerce.common.error.CommerceException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jakarta.persistence.EntityManager;
import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.MutationQuery;
import org.hibernate.type.descriptor.java.JdbcTimestampJavaType;
import org.hibernate.type.internal.BasicTypeImpl;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class QuickReorderPersistenceAdapter {
  private final EntityManager entities;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public QuickReorderPersistenceAdapter(EntityManager entities, ObjectMapper objectMapper, Clock clock) {
    this.entities = entities;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public ReorderResult reorder(long memberId, long sourceOrderId, String idempotencyKey) {
    lockMember(memberId);
    IdempotencyRow existing = one(query(
        "SELECT source_order_id AS sourceOrderId,response_json AS responseJson FROM quick_reorder_idempotency_results WHERE member_id=?1 AND idempotency_key=?2 FOR UPDATE",
        IdempotencyRow.class).addScalar("sourceOrderId", Long.class).addScalar("responseJson", String.class)
        .setParameter(1, memberId).setParameter(2, idempotencyKey));
    if (existing != null) {
      if (existing.sourceOrderId() != sourceOrderId) {
        throw new CommerceException(409, "IDEMPOTENCY_KEY_CONFLICT", "Idempotency-Key가 다른 주문에 사용되었습니다.");
      }
      return storedResponse(existing.responseJson());
    }
    if (query("SELECT COUNT(*) FROM orders WHERE id=?1 AND member_id=?2", Integer.class)
          .setParameter(1, sourceOrderId)
          .setParameter(2, memberId).getSingleResult()
        != 1) {
      throw new CommerceException(404, "ORDER_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    }
    CartLock cart = lockCart(memberId);
    List<SourceItem> sourceItems = query(
        "SELECT item.sku_id AS skuId,item.quantity,sku.status AS skuStatus,product.display_status AS productStatus,category.active AS categoryActive,inventory.available_quantity AS availableQuantity FROM order_items item JOIN skus sku ON sku.id=item.sku_id JOIN products product ON product.id=sku.product_id JOIN categories category ON category.id=product.category_id LEFT JOIN inventories inventory ON inventory.sku_id=sku.id WHERE item.order_id=?1 ORDER BY item.id FOR UPDATE",
        SourceItem.class).addScalar("skuId", Long.class).addScalar("quantity", Integer.class)
        .addScalar("skuStatus", String.class).addScalar("productStatus", String.class)
        .addScalar("categoryActive", Boolean.class).addScalar("availableQuantity", Integer.class)
        .setParameter(1, sourceOrderId).getResultList();
    List<ReorderItem> addedItems = new ArrayList<>();
    List<SkippedItem> skippedItems = new ArrayList<>();
    for (SourceItem sourceItem : sourceItems) {
      long skuId = sourceItem.skuId();
      int quantity = sourceItem.quantity();
      String reason = reorderSkipReason(sourceItem, quantity);
      if (reason != null) {
        skippedItems.add(new SkippedItem(skuId, quantity, reason));
        continue;
      }
      int changed =
          mutation("UPDATE cart_items SET quantity=quantity+?1 WHERE cart_id=?2 AND sku_id=?3")
          .setParameter(1, quantity)
          .setParameter(2, cart.id)
          .setParameter(3, skuId).executeUpdate();
      if (changed == 0) {
        mutation("INSERT INTO cart_items(cart_id,sku_id,quantity) VALUES (?1,?2,?3)")
          .setParameter(1, cart.id)
          .setParameter(2, skuId)
          .setParameter(3, quantity).executeUpdate();
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
      mutation("INSERT INTO quick_reorder_idempotency_results(member_id,idempotency_key,source_order_id,response_json,cart_version,created_at) VALUES (?1,?2,?3,?4,?5,?6)")
          .setParameter(1, memberId)
          .setParameter(2, idempotencyKey)
          .setParameter(3, sourceOrderId)
          .setParameter(4, objectMapper.writeValueAsString(json))
          .setParameter(5, resultCartVersion)
          .setParameter(6, now(), timestampType()).executeUpdate();
    } catch (Exception exception) {
      throw new IllegalStateException("Quick Reorder 결과를 저장할 수 없습니다.", exception);
    }
    return result;
  }

  private String reorderSkipReason(SourceItem sourceItem, int quantity) {
    if (!"ACTIVE".equals(sourceItem.skuStatus())
        || !"PUBLIC".equals(sourceItem.productStatus())
        || !sourceItem.categoryActive()) return "SKU_NOT_PURCHASABLE";
    Integer available = sourceItem.availableQuantity();
    if (available == null || available < quantity) return "OUT_OF_STOCK";
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
    Long locked = one(query("SELECT id FROM members WHERE id=?1 FOR UPDATE", Long.class)
        .setParameter(1, memberId));
    if (locked == null) throw new CommerceException(404, "MEMBER_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
  }

  private CartLock lockCart(long memberId) {
    Long cartId = one(query("SELECT id FROM carts WHERE member_id=?1 FOR UPDATE", Long.class)
        .setParameter(1, memberId));
    if (cartId == null) {
      mutation("INSERT INTO carts(member_id,created_at,updated_at) VALUES (?1,?2,?3)")
          .setParameter(1, memberId).setParameter(2, now(), timestampType())
          .setParameter(3, now(), timestampType()).executeUpdate();
      cartId = query("SELECT LAST_INSERT_ID()", Long.class).getSingleResult();
    }
    return one(query("SELECT id,version FROM carts WHERE id=?1 FOR UPDATE", CartLock.class)
        .addScalar("id", Long.class).addScalar("version", Long.class).setParameter(1, cartId));
  }

  private long incrementCartVersion(long cartId) {
    mutation("UPDATE carts SET version=version+1,updated_at=?1 WHERE id=?2")
        .setParameter(1, now(), timestampType()).setParameter(2, cartId).executeUpdate();
    return query("SELECT version FROM carts WHERE id=?1", Long.class).setParameter(1, cartId).getSingleResult();
  }

  private static <T> T one(NativeQuery<T> query) {
    List<T> rows = query.getResultList();
    return rows.isEmpty() ? null : rows.getFirst();
  }

  private <T> NativeQuery<T> query(String sql, Class<T> type) {
    // Keep pending managed changes invisible until the caller's original explicit flush.
    return entities.unwrap(Session.class).createNativeQuery(sql, type)
        .setHibernateFlushMode(FlushMode.COMMIT);
  }

  private MutationQuery mutation(String sql) {
    return entities.unwrap(Session.class).createNativeMutationQuery(sql)
        .setHibernateFlushMode(FlushMode.COMMIT);
  }

  private static final BasicTypeImpl<Timestamp> LEGACY_TIMESTAMP_TYPE =
      new BasicTypeImpl<>(JdbcTimestampJavaType.INSTANCE, new QuickReorderTimestampJdbcType());

  private static BasicTypeImpl<Timestamp> timestampType() {
    return LEGACY_TIMESTAMP_TYPE;
  }

  private Timestamp now() {
    return Timestamp.from(clock.instant());
  }

  private static long number(Map<?, ?> row, String key) {
    return ((Number) row.get(key)).longValue();
  }

  public record ReorderResult(List<ReorderItem> addedItems, List<SkippedItem> skippedItems, long cartVersion) {}

  public record ReorderItem(long skuId, int quantity) {}

  public record SkippedItem(long skuId, int quantity, String reason) {}

  // Hibernate native constructor projections require a public canonical constructor.
  public record CartLock(long id, long version) {}

  public record IdempotencyRow(long sourceOrderId, String responseJson) {}

  public record SourceItem(long skuId, int quantity, String skuStatus, String productStatus,
                            boolean categoryActive, Integer availableQuantity) {}
}
