package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.BrandView;

public record BrandResponse(
    Long brandId, String name, String slug, String logoUrl, boolean active, int displayOrder) {
  public static BrandResponse from(BrandView value) {
    return new BrandResponse(
        value.brandId(),
        value.name(),
        value.slug(),
        value.logoUrl(),
        value.active(),
        value.displayOrder());
  }
}
