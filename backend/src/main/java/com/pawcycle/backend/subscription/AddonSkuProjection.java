package com.pawcycle.backend.subscription;

import java.math.BigDecimal;

public record AddonSkuProjection(
    long skuId,
    long productId,
    String productName,
    String skuName,
    BigDecimal price,
    boolean eligible) {}
