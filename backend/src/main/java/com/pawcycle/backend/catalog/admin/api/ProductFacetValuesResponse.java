package com.pawcycle.backend.catalog.admin.api;

import java.util.List;

public record ProductFacetValuesResponse(Long productId, List<Long> facetOptionIds) {}
