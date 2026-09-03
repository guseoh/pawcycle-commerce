package com.pawcycle.backend.catalog.admin.api;

public record CategoryResponse(
    Long categoryId, Long parentId, String name, String slug, int displayOrder, boolean active) {
  public CategoryResponse(Long categoryId, String name, String slug, int displayOrder, boolean active) {
    this(categoryId, null, name, slug, displayOrder, active);
  }
}
