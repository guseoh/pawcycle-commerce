package com.pawcycle.backend.catalog.discovery.application;

import java.util.List;

public record CatalogDiscoveryResponse(
    List<CatalogCategoryResponse> categories,
    List<CatalogBrandResponse> brands,
    List<CatalogCategoryFacetsResponse> categoryFacets) {
  public CatalogDiscoveryResponse {
    categories = List.copyOf(categories == null ? List.of() : categories);
    brands = List.copyOf(brands == null ? List.of() : brands);
    categoryFacets = List.copyOf(categoryFacets == null ? List.of() : categoryFacets);
  }
}
