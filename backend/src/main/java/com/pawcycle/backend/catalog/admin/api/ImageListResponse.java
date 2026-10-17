package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.ImageListView;
import java.util.List;

public record ImageListResponse(List<ImageResponse> images) {
  public static ImageListResponse from(ImageListView value) {
    return new ImageListResponse(
        value.images().stream().map(ImageResponse::from).toList());
  }
}
