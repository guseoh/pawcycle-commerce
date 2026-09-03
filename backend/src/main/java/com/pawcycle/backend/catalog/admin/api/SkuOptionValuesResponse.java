package com.pawcycle.backend.catalog.admin.api;

import java.util.List;

public record SkuOptionValuesResponse(Long skuId, List<Long> optionValueIds) {}
