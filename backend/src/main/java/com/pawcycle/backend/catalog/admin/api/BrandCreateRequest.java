package com.pawcycle.backend.catalog.admin.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
public record BrandCreateRequest(
    @NotBlank @Size(max = 150) String name,
    @NotBlank @Size(max = 100) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
    @Size(max = 2048) String logoUrl,
    @NotNull Boolean active,
    @NotNull @PositiveOrZero Integer displayOrder) {}
