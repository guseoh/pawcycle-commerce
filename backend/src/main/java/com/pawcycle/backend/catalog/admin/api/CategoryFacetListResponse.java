package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.CategoryFacetListView;
import java.util.List;

public record CategoryFacetListResponse(Long categoryId, List<CategoryFacetResponse> facets) {
  public static CategoryFacetListResponse from(CategoryFacetListView value) {
    return new CategoryFacetListResponse(
        value.categoryId(), value.facets().stream().map(CategoryFacetResponse::from).toList());
  }
}
