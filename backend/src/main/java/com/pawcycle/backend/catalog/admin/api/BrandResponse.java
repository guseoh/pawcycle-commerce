package com.pawcycle.backend.catalog.admin.api;

public record BrandResponse(
    Long brandId, String name, String slug, String logoUrl, boolean active, int displayOrder) {}
