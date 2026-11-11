package com.pawcycle.backend.catalog.admin.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
public record CategoryFacetAssignRequest(@NotNull @PositiveOrZero Integer displayOrder) {}
