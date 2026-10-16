package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.FacetDefinitionListView;
import java.util.List;

public record FacetDefinitionListResponse(List<FacetDefinitionResponse> facetDefinitions) {
  public static FacetDefinitionListResponse from(FacetDefinitionListView value) {
    return new FacetDefinitionListResponse(
        value.facetDefinitions().stream().map(FacetDefinitionResponse::from).toList());
  }
}
