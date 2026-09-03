package com.pawcycle.backend.catalog.admin.api;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.pawcycle.backend.catalog.product.domain.ProductStatus;
import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
public record ProductCreateRequest(
    @NotNull @Positive Long categoryId,
    @NotNull @Positive Long brandId,
    @NotBlank @Size(max = 200) String name,
    @NotBlank @Size(max = 500) String shortDescription,
    @Size(max = 2000) String description,
    @NotBlank @Size(max = 20) String petType,
    @Size(max = 2048) String thumbnailUrl) {}
