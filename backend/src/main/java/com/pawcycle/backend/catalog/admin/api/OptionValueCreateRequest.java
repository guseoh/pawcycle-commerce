package com.pawcycle.backend.catalog.admin.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
public record OptionValueCreateRequest(
    @NotBlank @Size(max = 100) String value, @NotNull @PositiveOrZero Integer displayOrder) {}
