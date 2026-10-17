package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.OptionGroupView;
import java.util.List;

public record OptionGroupResponse(
    Long optionGroupId,
    Long productId,
    String name,
    int displayOrder,
    List<OptionValueResponse> values) {
  public static OptionGroupResponse from(OptionGroupView value) {
    return new OptionGroupResponse(
        value.optionGroupId(),
        value.productId(),
        value.name(),
        value.displayOrder(),
        value.values().stream().map(OptionValueResponse::from).toList());
  }
}
