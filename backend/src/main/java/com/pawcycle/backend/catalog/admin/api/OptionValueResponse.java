package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.OptionValueView;

public record OptionValueResponse(
    Long optionValueId, Long optionGroupId, String value, int displayOrder) {
  public static OptionValueResponse from(OptionValueView value) {
    return new OptionValueResponse(
        value.optionValueId(), value.optionGroupId(), value.value(), value.displayOrder());
  }
}
