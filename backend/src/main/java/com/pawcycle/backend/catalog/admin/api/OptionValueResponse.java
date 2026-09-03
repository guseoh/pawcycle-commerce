package com.pawcycle.backend.catalog.admin.api;

public record OptionValueResponse(
    Long optionValueId, Long optionGroupId, String value, int displayOrder) {}
