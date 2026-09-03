package com.pawcycle.backend.catalog.admin.api;

import java.util.List;

public record FacetDefinitionResponse(
    Long facetDefinitionId, String key, String name, List<FacetOptionResponse> options) {}
