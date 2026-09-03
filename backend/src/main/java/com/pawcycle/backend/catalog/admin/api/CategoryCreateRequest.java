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
public record CategoryCreateRequest(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Size(max = 100) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
    @NotNull @PositiveOrZero Integer displayOrder,
    @NotNull Boolean active,
    @Positive Long parentId) {
  public CategoryCreateRequest(String name, String slug, Integer displayOrder, Boolean active) {
    this(name, slug, displayOrder, active, null);
  }
}
