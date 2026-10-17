package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.SkuView;
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
  public static SkuResponse from(SkuView value) {
    return new SkuResponse(
        value.skuId(),
        value.productId(),
        value.skuCode(),
        value.name(),
        value.price(),
        value.compareAtPrice(),
        value.subscribable(),
        value.displayOrder(),
        value.status());
  }

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
