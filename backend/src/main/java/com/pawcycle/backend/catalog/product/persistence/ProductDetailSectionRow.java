package com.pawcycle.backend.catalog.product.persistence;

import com.pawcycle.backend.catalog.product.application.ProductDetailSectionView;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public record ProductDetailSectionRow(
    Long id,
    String title,
    String body,
    int displayOrder,
    boolean visible,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {
  public ProductDetailSectionView toView() {
    return new ProductDetailSectionView(
        id,
        title,
        body,
        displayOrder,
        visible,
        createdAt.toInstant(ZoneOffset.UTC),
        updatedAt.toInstant(ZoneOffset.UTC));
  }
}
