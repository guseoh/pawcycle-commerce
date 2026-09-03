package com.pawcycle.backend.catalog.product.application;

public record ProductImage(
    Long imageId, String imageUrl, String altText, int displayOrder, String imageType) {}
