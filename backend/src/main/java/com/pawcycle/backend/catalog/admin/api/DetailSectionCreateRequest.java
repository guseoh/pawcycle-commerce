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
public record DetailSectionCreateRequest(
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 10000) String body,
    @NotNull @PositiveOrZero Integer displayOrder,
    @NotNull Boolean visible) {}
