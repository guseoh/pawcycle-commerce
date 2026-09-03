package com.pawcycle.backend.catalog.discovery.application;

public record CatalogBrandResponse(
    Long brandId, String name, String slug, String logoUrl, int displayOrder) {}
