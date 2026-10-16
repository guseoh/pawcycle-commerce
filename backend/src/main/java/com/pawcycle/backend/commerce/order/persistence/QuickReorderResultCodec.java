package com.pawcycle.backend.commerce.order.persistence;

import com.pawcycle.backend.commerce.common.error.CommerceException;
import com.pawcycle.backend.commerce.order.persistence.QuickReorderPersistenceAdapter.ReorderItem;
import com.pawcycle.backend.commerce.order.persistence.QuickReorderPersistenceAdapter.ReorderResult;
import com.pawcycle.backend.commerce.order.persistence.QuickReorderPersistenceAdapter.SkippedItem;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

/** Durable replay format, including legacy permissive numeric and non-map added-item behavior. */
final class QuickReorderResultCodec {
  private final ObjectMapper objectMapper;

  QuickReorderResultCodec(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  String encode(ReorderResult result) {
    Map<String, Object> json = new LinkedHashMap<>();
    json.put("addedItems", result.addedItems());
    json.put("skippedItems", result.skippedItems());
    json.put("cartVersion", result.cartVersion());
    // The caller retains the existing storage exception boundary, including database failures.
    return objectMapper.writeValueAsString(json);
  }

  ReorderResult decode(String responseJson) {
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

  private static long number(Map<?, ?> row, String key) {
    return ((Number) row.get(key)).longValue();
  }
}
