package com.pawcycle.backend.commerce.order.application;

import com.pawcycle.backend.commerce.CommerceException;
import com.pawcycle.backend.commerce.CommercePayload;
import com.pawcycle.backend.commerce.CommerceRowResponse;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
public class OrderApplicationService {
  private final NativeQueryExecutor jdbc;
  private final TransactionTemplate transaction;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final int returnRequestDays;

  public OrderApplicationService(
      NativeQueryExecutor jdbc,
      org.springframework.transaction.PlatformTransactionManager transactionManager,
      ObjectMapper objectMapper,
      Clock clock,
      @Value("${pawcycle.commerce.return-request-days:7}") int returnRequestDays) {
    this.jdbc = jdbc;
    this.transaction = new TransactionTemplate(transactionManager);
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.returnRequestDays = returnRequestDays;
  }

  public List<CommerceRowResponse> orders(long memberId) {
    return CommerceRowResponse.from(
        jdbc.queryForList(
            "SELECT id AS orderId,order_number AS orderNumber,source,status,payment_amount AS"
                + " paymentAmount,created_at AS createdAt,paid_at AS paidAt FROM orders WHERE"
                + " member_id=? ORDER BY id DESC",
            memberId));
  }

  public CommercePayload order(long memberId, long orderId) {
    Map<String, Object> order =
        one(
            "SELECT id AS orderId,order_number AS orderNumber,source,status,original_amount AS"
                + " originalAmount,discount_amount AS discountAmount,shipping_fee AS"
                + " shippingFee,payment_amount AS paymentAmount,recipient_name AS"
                + " recipientName,recipient_phone AS recipientPhone,postal_code AS"
                + " postalCode,address_line1 AS addressLine1,address_line2 AS"
                + " addressLine2,created_at AS createdAt,paid_at AS paidAt FROM orders WHERE id=?"
                + " AND member_id=?",
            orderId,
            memberId);
    if (order == null) notFound("ORDER_NOT_FOUND");
    order.put(
        "items",
        jdbc.queryForList(
            "SELECT sku_id AS skuId,snapshot_quality AS snapshotQuality,sku_code_snapshot AS"
                + " skuCodeSnapshot,product_name_snapshot AS productNameSnapshot,sku_name_snapshot"
                + " AS skuNameSnapshot,unit_price AS unitPrice,quantity,line_amount AS lineAmount"
                + " FROM order_items WHERE order_id=? ORDER BY id",
            orderId));
    order.put(
        "payment",
        one(
            "SELECT id AS paymentId,type,provider,status,amount,attempt_no AS"
                + " attemptNo,provider_status AS providerStatus FROM payments WHERE order_id=?"
                + " ORDER BY attempt_no DESC LIMIT 1",
            orderId));
    Map<String, Object> delivery =
        one(
            "SELECT id AS deliveryId,status,carrier_code AS carrierCode,tracking_number AS"
                + " trackingNumber,failure_reason AS failureReason,shipped_at AS"
                + " shippedAt,delivered_at AS deliveredAt FROM deliveries WHERE order_id=?",
            orderId);
    order.put("delivery", delivery);
    order.put(
        "cancellation",
        one(
            "SELECT id AS cancellationId,status,reason,requested_at AS requestedAt,completed_at AS"
                + " completedAt FROM order_cancellations WHERE order_id=?",
            orderId));
    order.put(
        "return",
        one(
            "SELECT id AS returnId,status,reason,rejection_reason AS"
                + " rejectionReason,restock,requested_at AS requestedAt,received_at AS"
                + " receivedAt,completed_at AS completedAt FROM order_returns WHERE order_id=?",
            orderId));
    order.put(
        "refunds",
        jdbc.queryForList(
            "SELECT id AS refundId,source,status,amount,attempt_no AS"
                + " attemptNo,reconciliation_attempts AS reconciliationAttempts FROM refunds WHERE"
                + " order_id=? ORDER BY attempt_no",
            orderId));
    List<String> actions = new ArrayList<>();
    if ("PAID".equals(order.get("status"))
        && delivery != null
        && "PREPARING".equals(delivery.get("status"))
        && order.get("cancellation") == null) actions.add("REQUEST_CANCELLATION");
    Timestamp deliveredAt = delivery == null ? null : (Timestamp) delivery.get("deliveredAt");
    boolean returnWindowOpen =
        deliveredAt != null
            && !deliveredAt.toInstant().plus(returnRequestDays, ChronoUnit.DAYS).isBefore(clock.instant());
    if (delivery != null
        && "DELIVERED".equals(delivery.get("status"))
        && returnWindowOpen
        && order.get("return") == null) actions.add("REQUEST_RETURN");
    order.put("availableActions", actions);
    return CommercePayload.from(order);
  }

  public CommercePayload reorder(long memberId, long sourceOrderId, String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
      throw new CommerceException(400, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key가 필요합니다.");
    }
    return CommercePayload.from(
        transaction.execute(
            status -> {
              lockMember(memberId);
              Map<String, Object> existing =
                  one(
                      "SELECT source_order_id AS sourceOrderId,response_json AS responseJson FROM"
                          + " quick_reorder_idempotency_results WHERE member_id=? AND idempotency_key=?"
                          + " FOR UPDATE",
                      memberId,
                      idempotencyKey);
              if (existing != null) {
                if (number(existing, "sourceOrderId") != sourceOrderId) {
                  throw new CommerceException(
                      409, "IDEMPOTENCY_KEY_CONFLICT", "Idempotency-Key가 다른 주문에 사용되었습니다.");
                }
                return storedResponse((String) existing.get("responseJson"));
              }

              if (jdbc.queryForObject(
                      "SELECT COUNT(*) FROM orders WHERE id=? AND member_id=?",
                      Integer.class,
                      sourceOrderId,
                      memberId)
                  != 1) {
                notFound("ORDER_NOT_FOUND");
              }
              CartLock cart = lockCart(memberId);
              List<Map<String, Object>> sourceItems =
                  jdbc.queryForList(
                      "SELECT item.sku_id AS skuId,item.quantity,sku.status AS skuStatus,"
                          + "product.display_status AS productStatus,category.active AS categoryActive,"
                          + "inventory.available_quantity AS availableQuantity FROM order_items item JOIN"
                          + " skus sku ON sku.id=item.sku_id JOIN products product ON product.id=sku.product_id"
                          + " JOIN categories category ON category.id=product.category_id LEFT JOIN"
                          + " inventories inventory ON inventory.sku_id=sku.id WHERE item.order_id=?"
                          + " ORDER BY item.id FOR UPDATE",
                      sourceOrderId);
              List<Map<String, Object>> addedItems = new ArrayList<>();
              List<Map<String, Object>> skippedItems = new ArrayList<>();
              for (Map<String, Object> sourceItem : sourceItems) {
                long skuId = number(sourceItem, "skuId");
                int quantity = (int) number(sourceItem, "quantity");
                String reason = reorderSkipReason(sourceItem, quantity);
                if (reason != null) {
                  skippedItems.add(Map.of("skuId", skuId, "quantity", quantity, "reason", reason));
                  continue;
                }
                int changed =
                    jdbc.update(
                        "UPDATE cart_items SET quantity=quantity+? WHERE cart_id=? AND sku_id=?",
                        quantity,
                        cart.id(),
                        skuId);
                if (changed == 0) {
                  jdbc.update(
                      "INSERT INTO cart_items(cart_id,sku_id,quantity) VALUES (?,?,?)",
                      cart.id(),
                      skuId,
                      quantity);
                }
                addedItems.add(Map.of("skuId", skuId, "quantity", quantity));
              }
              long resultCartVersion = cart.version();
              if (!addedItems.isEmpty()) resultCartVersion = incrementCartVersion(cart.id());
              Map<String, Object> result = new LinkedHashMap<>();
              result.put("addedItems", addedItems);
              result.put("skippedItems", skippedItems);
              result.put("cartVersion", resultCartVersion);
              try {
                jdbc.update(
                    "INSERT INTO"
                        + " quick_reorder_idempotency_results(member_id,idempotency_key,source_order_id,response_json,cart_version,created_at)"
                        + " VALUES (?,?,?,?,?,?)",
                    memberId,
                    idempotencyKey,
                    sourceOrderId,
                    objectMapper.writeValueAsString(result),
                    resultCartVersion,
                    now());
              } catch (Exception exception) {
                throw new IllegalStateException("Quick Reorder 결과를 저장할 수 없습니다.", exception);
              }
              return result;
            }));
  }

  private String reorderSkipReason(Map<String, Object> sourceItem, int quantity) {
    if (!"ACTIVE".equals(sourceItem.get("skuStatus"))
        || !"PUBLIC".equals(sourceItem.get("productStatus"))
        || !booleanValue(sourceItem.get("categoryActive"))) return "SKU_NOT_PURCHASABLE";
    Object available = sourceItem.get("availableQuantity");
    if (!(available instanceof Number number) || number.intValue() < quantity) return "OUT_OF_STOCK";
    return null;
  }

  private Map<String, Object> storedResponse(String responseJson) {
    try {
      var responseType =
          objectMapper
              .getTypeFactory()
              .constructMapType(LinkedHashMap.class, String.class, Object.class);
      return objectMapper.readValue(responseJson, responseType);
    } catch (Exception exception) {
      throw new CommerceException(
          409, "IDEMPOTENCY_KEY_CONFLICT", "저장된 Quick Reorder 결과를 읽을 수 없습니다.");
    }
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
          "INSERT INTO carts(member_id,created_at,updated_at) VALUES (?,?,?)", memberId, now(), now());
      cartId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
    Map<String, Object> row = one("SELECT id,version FROM carts WHERE id=? FOR UPDATE", cartId);
    return new CartLock(cartId, number(row, "version"));
  }

  private long incrementCartVersion(long cartId) {
    jdbc.update("UPDATE carts SET version=version+1,updated_at=? WHERE id=?", now(), cartId);
    return jdbc.queryForObject("SELECT version FROM carts WHERE id=?", Long.class, cartId);
  }

  private Map<String, Object> one(String sql, Object... args) {
    List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
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

  private static long number(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).longValue();
  }

  private static void notFound(String code) {
    throw new CommerceException(404, code, "요청한 리소스를 찾을 수 없습니다.");
  }

  private record CartLock(long id, long version) {}
}