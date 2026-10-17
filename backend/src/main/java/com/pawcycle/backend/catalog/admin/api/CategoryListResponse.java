package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.CategoryView;
import java.util.List;

public record CategoryListResponse(List<CategoryResponse> categories) {
  public static CategoryListResponse from(List<CategoryView> values) {
    return new CategoryListResponse(values.stream().map(CategoryResponse::from).toList());
  }
}
