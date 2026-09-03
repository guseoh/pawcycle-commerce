package com.pawcycle.backend.catalog.admin.api;

public record FacetOptionResponse(
    Long facetOptionId, Long facetDefinitionId, String value, int displayOrder) {}
