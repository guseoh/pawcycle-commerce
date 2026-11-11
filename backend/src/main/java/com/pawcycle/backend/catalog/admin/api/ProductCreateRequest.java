package com.pawcycle.backend.catalog.admin.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
public record ProductCreateRequest(
    @NotNull @Positive Long categoryId,
    @NotNull @Positive Long brandId,
    @NotBlank @Size(max = 200) String name,
    @NotBlank @Size(max = 500) String shortDescription,
    @Size(max = 2000) String description,
    @NotBlank @Size(max = 20) String petType,
    @Size(max = 2048) String thumbnailUrl) {}
