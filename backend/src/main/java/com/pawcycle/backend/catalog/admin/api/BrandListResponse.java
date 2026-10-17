package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.BrandView;
import java.util.List;

public record BrandListResponse(List<BrandResponse> brands) {
  public static BrandListResponse from(List<BrandView> values) {
    return new BrandListResponse(values.stream().map(BrandResponse::from).toList());
  }
}
