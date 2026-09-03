package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.product.domain.ProductStatus;

public record ProductResponse(
    Long productId,
    Long categoryId,
    Long brandId,
    String name,
    String shortDescription,
    String description,
    String petType,
    String thumbnailUrl,
    ProductStatus status) {
  public ProductResponse(
      Long productId,
      Long categoryId,
      String name,
      String shortDescription,
      String description,
      String petType,
      String thumbnailUrl,
      ProductStatus status) {
    this(
        productId,
        categoryId,
        1L,
        name,
        shortDescription,
        description,
        petType,
        thumbnailUrl,
        status);
  }
}
