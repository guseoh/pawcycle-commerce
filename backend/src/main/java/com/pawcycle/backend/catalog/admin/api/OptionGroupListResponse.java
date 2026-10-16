package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.OptionGroupListView;
import java.util.List;

public record OptionGroupListResponse(List<OptionGroupResponse> optionGroups) {
  public static OptionGroupListResponse from(OptionGroupListView value) {
    return new OptionGroupListResponse(
        value.optionGroups().stream().map(OptionGroupResponse::from).toList());
  }
}
