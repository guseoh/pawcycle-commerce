package com.pawcycle.backend.catalog.product.application;

import java.util.List;

public record ProductComparisonResponse(
    List<ProductComparisonFacts> products, String aiStatus, String aiSummary) {
  public ProductComparisonResponse {
    products = List.copyOf(products);
  }
}
