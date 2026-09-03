package com.pawcycle.backend.catalog.discovery.application;

import java.util.List;

public record CatalogCategoryFacetsResponse(
    String categorySlug, List<CatalogFacetResponse> facets) {
  public CatalogCategoryFacetsResponse {
    facets = List.copyOf(facets == null ? List.of() : facets);
  }
}
