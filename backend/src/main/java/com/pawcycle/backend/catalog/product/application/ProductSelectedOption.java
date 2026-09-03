package com.pawcycle.backend.catalog.product.application;

public record ProductSelectedOption(
    Long optionGroupId, String groupName, Long optionValueId, String value) {}
