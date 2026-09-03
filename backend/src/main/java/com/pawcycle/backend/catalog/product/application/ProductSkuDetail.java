package com.pawcycle.backend.catalog.product.application;

import java.math.BigDecimal;
import java.util.List;

public record ProductSkuDetail(
    Long skuId,
    String skuName,
    BigDecimal price,
    boolean subscribable,
    List<Integer> availableDeliveryCycles,
    int availableQuantity,
    boolean purchasable,
    BigDecimal compareAtPrice,
    Integer discountRate,
    List<ProductSelectedOption> selectedOptions) {

  public ProductSkuDetail(
      Long skuId,
      String skuName,
      BigDecimal price,
      boolean subscribable,
      List<Integer> availableDeliveryCycles,
      int availableQuantity,
      boolean purchasable) {
    this(
        skuId,
        skuName,
        price,
        subscribable,
        availableDeliveryCycles,
        availableQuantity,
        purchasable,
        null,
        null,
        List.of());
  }

  public ProductSkuDetail(
      Long skuId,
      String skuName,
      BigDecimal price,
      boolean subscribable,
      List<Integer> availableDeliveryCycles) {
    this(skuId, skuName, price, subscribable, availableDeliveryCycles, 0, true, null, null, List.of());
  }

  public ProductSkuDetail {
    availableDeliveryCycles = List.copyOf(availableDeliveryCycles);
    selectedOptions = List.copyOf(selectedOptions == null ? List.of() : selectedOptions);
  }
}
