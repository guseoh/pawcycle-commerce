package com.pawcycle.backend.catalog.product.application;

import java.util.List;

public record ProductListSnapshot(List<ProductSnapshot> products, List<SkuSnapshot> skus) {
  public ProductListSnapshot {
    products = List.copyOf(products);
    skus = List.copyOf(skus);
  }
}
