package com.pawcycle.backend.subscription.api;

public record SubscriptionItemDetailResponse(
    long skuId,
    String skuName,
    long productId,
    String productName,
    String thumbnailUrl,
    int quantity) {}
