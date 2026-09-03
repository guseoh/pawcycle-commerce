package com.pawcycle.backend.subscription;

public record SubscriptionItemDetailProjection(
    long skuId,
    String skuName,
    long productId,
    String productName,
    String thumbnailUrl,
    int quantity) {}
