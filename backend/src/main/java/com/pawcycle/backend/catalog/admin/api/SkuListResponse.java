package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.SkuView;
import java.util.List;

public record SkuListResponse(List<SkuResponse> skus) {
  public static SkuListResponse from(List<SkuView> values) {
    return new SkuListResponse(values.stream().map(SkuResponse::from).toList());
  }
}
