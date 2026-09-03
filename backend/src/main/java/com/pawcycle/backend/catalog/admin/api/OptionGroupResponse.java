package com.pawcycle.backend.catalog.admin.api;

import java.util.List;

public record OptionGroupResponse(
    Long optionGroupId,
    Long productId,
    String name,
    int displayOrder,
    List<OptionValueResponse> values) {}
