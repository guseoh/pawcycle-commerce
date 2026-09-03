package com.pawcycle.backend.catalog.product.application;

import java.math.BigDecimal;

public record ProductTrust(BigDecimal averageRating, long reviewCount, long questionCount) {
  public static ProductTrust empty() {
    return new ProductTrust(null, 0, 0);
  }
}
