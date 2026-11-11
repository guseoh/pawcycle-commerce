package com.pawcycle.backend.catalog.admin.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
public record ProductFacetValuesRequest(@NotNull java.util.List<@Positive Long> facetOptionIds) {}
