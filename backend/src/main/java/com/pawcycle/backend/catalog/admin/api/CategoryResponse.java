package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.CategoryView;

public record CategoryResponse(
    Long categoryId, Long parentId, String name, String slug, int displayOrder, boolean active) {
  public CategoryResponse(Long categoryId, String name, String slug, int displayOrder, boolean active) {
    this(categoryId, null, name, slug, displayOrder, active);
  }

  public static CategoryResponse from(CategoryView value) {
    return new CategoryResponse(
        value.categoryId(),
        value.parentId(),
        value.name(),
        value.slug(),
        value.displayOrder(),
        value.active());
  }
}
