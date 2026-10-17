package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.DetailSectionListView;
import java.util.List;

public record DetailSectionListResponse(List<DetailSectionResponse> detailSections) {
  public static DetailSectionListResponse from(DetailSectionListView value) {
    return new DetailSectionListResponse(
        value.detailSections().stream().map(DetailSectionResponse::from).toList());
  }
}
