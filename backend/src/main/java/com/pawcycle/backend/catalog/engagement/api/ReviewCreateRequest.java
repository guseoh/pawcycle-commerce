package com.pawcycle.backend.catalog.engagement.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewCreateRequest(
    @NotNull @Min(1) @Max(5) Integer rating, @NotBlank @Size(max = 10000) String content) {}
