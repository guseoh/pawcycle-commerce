package com.pawcycle.backend.catalog.admin.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
public record OptionGroupCreateRequest(
    @NotBlank @Size(max = 100) String name, @NotNull @PositiveOrZero Integer displayOrder) {}
