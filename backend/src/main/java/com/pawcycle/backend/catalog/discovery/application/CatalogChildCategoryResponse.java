package com.pawcycle.backend.catalog.discovery.application;

public record CatalogChildCategoryResponse(
    Long categoryId, String name, String slug, int displayOrder) {}
