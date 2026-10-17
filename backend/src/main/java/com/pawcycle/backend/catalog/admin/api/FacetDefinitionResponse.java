package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.FacetDefinitionView;
import java.util.List;

public record FacetDefinitionResponse(
    Long facetDefinitionId, String key, String name, List<FacetOptionResponse> options) {
  public static FacetDefinitionResponse from(FacetDefinitionView value) {
    return new FacetDefinitionResponse(
        value.facetDefinitionId(),
        value.key(),
        value.name(),
        value.options().stream().map(FacetOptionResponse::from).toList());
  }
}
