package com.pawcycle.backend.catalog.product.application;

import java.math.BigDecimal;
import java.util.List;

public record ProductDetailSkuRow(
    Long skuId,
    String skuName,
    BigDecimal price,
    BigDecimal compareAtPrice,
    boolean subscribable,
    int availableQuantity,
    List<ProductSelectedOption> selectedOptions) {}
