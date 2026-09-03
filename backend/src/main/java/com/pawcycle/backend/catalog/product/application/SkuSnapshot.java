package com.pawcycle.backend.catalog.product.application;

import java.math.BigDecimal;

public record SkuSnapshot(
    Long productId, Long skuId, String skuName, BigDecimal price, boolean subscribable) {}
