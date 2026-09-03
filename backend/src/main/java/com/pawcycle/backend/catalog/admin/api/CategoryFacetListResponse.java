package com.pawcycle.backend.catalog.admin.api;

import java.util.List;

public record CategoryFacetListResponse(Long categoryId, List<CategoryFacetResponse> facets) {}
