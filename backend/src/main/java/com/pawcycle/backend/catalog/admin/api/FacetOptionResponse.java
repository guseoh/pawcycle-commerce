package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.FacetOptionView;

public record FacetOptionResponse(
    Long facetOptionId, Long facetDefinitionId, String value, int displayOrder) {
  public static FacetOptionResponse from(FacetOptionView value) {
    return new FacetOptionResponse(
        value.facetOptionId(), value.facetDefinitionId(), value.value(), value.displayOrder());
  }
}
