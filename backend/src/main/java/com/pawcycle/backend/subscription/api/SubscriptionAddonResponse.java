package com.pawcycle.backend.subscription.api;

import java.math.BigDecimal;

public record SubscriptionAddonResponse(
    long skuId,
    long productId,
    String productName,
    String skuName,
    int quantity,
    BigDecimal unitPriceKrw,
    BigDecimal lineAmountKrw) {}
