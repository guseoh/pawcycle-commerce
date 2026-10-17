package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.ProductView;
import java.util.List;

public record ProductListResponse(List<ProductResponse> products) {
  public static ProductListResponse from(List<ProductView> values) {
    return new ProductListResponse(values.stream().map(ProductResponse::from).toList());
  }
}
