package com.pawcycle.backend.catalog.discovery.application;

import java.util.List;

public record CatalogFacetResponse(
    String key, String name, int displayOrder, List<CatalogFacetOptionResponse> options) {
  public CatalogFacetResponse {
    options = List.copyOf(options == null ? List.of() : options);
  }
}
