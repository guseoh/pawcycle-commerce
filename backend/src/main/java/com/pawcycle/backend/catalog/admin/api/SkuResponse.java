package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import java.math.BigDecimal;

public record SkuResponse(
    Long skuId,
    Long productId,
    String skuCode,
    String name,
    BigDecimal price,
    BigDecimal compareAtPrice,
    boolean subscribable,
    int displayOrder,
    SkuStatus status) {
  public SkuResponse(
      Long skuId,
      Long productId,
      String skuCode,
      String name,
      BigDecimal price,
      boolean subscribable,
      int displayOrder,
      SkuStatus status) {
    this(skuId, productId, skuCode, name, price, null, subscribable, displayOrder, status);
  }
}
