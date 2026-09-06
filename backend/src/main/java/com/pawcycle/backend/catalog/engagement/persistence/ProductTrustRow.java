package com.pawcycle.backend.catalog.engagement.persistence;

import java.math.BigDecimal;

public final class ProductTrustRow {
  private final BigDecimal averageRating;
  private final long reviewCount;

  public ProductTrustRow(Double averageRating, Long reviewCount) {
    this.averageRating = averageRating == null ? null : BigDecimal.valueOf(averageRating);
    this.reviewCount = reviewCount == null ? 0 : reviewCount;
  }

  public BigDecimal averageRating() {
    return averageRating;
  }

  public long reviewCount() {
    return reviewCount;
  }
}
