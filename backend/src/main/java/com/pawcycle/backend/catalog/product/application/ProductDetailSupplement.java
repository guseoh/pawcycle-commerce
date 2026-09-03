package com.pawcycle.backend.catalog.product.application;

import java.util.List;

public record ProductDetailSupplement(
    BrandSummary brand, List<ProductImage> images, List<ProductOptionGroup> optionGroups) {
  public static ProductDetailSupplement empty() {
    return new ProductDetailSupplement(null, List.of(), List.of());
  }
}
