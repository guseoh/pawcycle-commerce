package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.CategoryFacetView;

public record CategoryFacetResponse(Long categoryId, Long facetDefinitionId, int displayOrder) {
  public static CategoryFacetResponse from(CategoryFacetView value) {
    return new CategoryFacetResponse(
        value.categoryId(), value.facetDefinitionId(), value.displayOrder());
  }
}
