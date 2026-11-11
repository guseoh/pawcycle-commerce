package com.pawcycle.backend.catalog.admin.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
public record ImageCreateRequest(
    @NotBlank @Size(max = 2048) String imageUrl,
    @Size(max = 500) String altText,
    @NotNull @PositiveOrZero Integer displayOrder,
    @NotBlank @Pattern(regexp = "MAIN|DETAIL") String imageType) {}
