package com.pawcycle.backend.catalog.product.application;

import java.util.List;

public record ProductOptionGroup(
    Long optionGroupId, String name, int displayOrder, List<ProductOptionValue> values) {
  public ProductOptionGroup {
    values = List.copyOf(values);
  }
}
