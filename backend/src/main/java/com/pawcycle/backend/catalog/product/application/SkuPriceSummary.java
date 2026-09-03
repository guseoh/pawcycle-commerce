package com.pawcycle.backend.catalog.product.application;

import java.util.List;

public record SkuPriceSummary(List<SkuPrice> skuPrices) {
  public SkuPriceSummary {
    skuPrices = List.copyOf(skuPrices);
  }
}
