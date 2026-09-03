package com.pawcycle.backend.catalog.product.application;

import java.math.BigDecimal;

public record SkuPrice(Long skuId, String skuName, BigDecimal price) {}
