package com.pawcycle.backend.catalog.product.persistence;

import com.pawcycle.backend.catalog.product.application.ProductDetailSectionView;
import java.time.LocalDateTime;

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
        createdAt.atZone(java.time.ZoneId.systemDefault()).toInstant(),
        updatedAt.atZone(java.time.ZoneId.systemDefault()).toInstant());
  }
}
