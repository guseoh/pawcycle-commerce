package com.pawcycle.backend.catalog.admin.api;

public record ImageResponse(
    Long imageId,
    Long productId,
    String imageUrl,
    String altText,
    int displayOrder,
    String imageType) {}
