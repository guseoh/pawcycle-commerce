package com.pawcycle.backend.catalog.discovery.application;

import java.util.List;

public record CatalogCategoryResponse(
    Long categoryId,
    String name,
    String slug,
    int displayOrder,
    List<CatalogChildCategoryResponse> children) {
  public CatalogCategoryResponse {
    children = List.copyOf(children == null ? List.of() : children);
  }
}
