package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.ProductFacetValuesView;
import java.util.List;

public record ProductFacetValuesResponse(Long productId, List<Long> facetOptionIds) {
  public static ProductFacetValuesResponse from(ProductFacetValuesView value) {
    return new ProductFacetValuesResponse(value.productId(), value.facetOptionIds());
  }
}
