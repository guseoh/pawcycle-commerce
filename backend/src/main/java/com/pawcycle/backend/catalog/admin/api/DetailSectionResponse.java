package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.DetailSectionView;
import java.time.Instant;

public record DetailSectionResponse(
    Long sectionId,
    Long productId,
    String title,
    String body,
    int displayOrder,
    boolean visible,
    Instant createdAt,
    Instant updatedAt) {
  public static DetailSectionResponse from(DetailSectionView value) {
    return new DetailSectionResponse(
        value.sectionId(),
        value.productId(),
        value.title(),
        value.body(),
        value.displayOrder(),
        value.visible(),
        value.createdAt(),
        value.updatedAt());
  }
}
