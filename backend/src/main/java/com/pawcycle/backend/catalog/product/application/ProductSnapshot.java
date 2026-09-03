package com.pawcycle.backend.catalog.product.application;

public record ProductSnapshot(
    Long productId,
    String name,
    String petType,
    String shortDescription,
    String thumbnailUrl,
    CategorySnapshot category) {}
