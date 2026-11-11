package com.pawcycle.backend.catalog.admin.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
public record DetailSectionCreateRequest(
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 10000) String body,
    @NotNull @PositiveOrZero Integer displayOrder,
    @NotNull Boolean visible) {}
