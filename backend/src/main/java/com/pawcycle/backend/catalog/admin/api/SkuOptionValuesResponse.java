package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.SkuOptionValuesView;
import java.util.List;

public record SkuOptionValuesResponse(Long skuId, List<Long> optionValueIds) {
  public static SkuOptionValuesResponse from(SkuOptionValuesView value) {
    return new SkuOptionValuesResponse(value.skuId(), value.optionValueIds());
  }
}
