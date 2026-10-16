package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.ImageView;

public record ImageResponse(
    Long imageId,
    Long productId,
    String imageUrl,
    String altText,
    int displayOrder,
    String imageType) {
  public static ImageResponse from(ImageView value) {
    return new ImageResponse(
        value.imageId(),
        value.productId(),
        value.imageUrl(),
        value.altText(),
        value.displayOrder(),
        value.imageType());
  }
}
