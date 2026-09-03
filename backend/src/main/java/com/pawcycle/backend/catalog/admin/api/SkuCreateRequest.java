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
public record SkuCreateRequest(
    @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*") String skuCode,
    @NotBlank @Size(max = 200) String name,
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
    @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal compareAtPrice,
    @NotNull Boolean subscribable,
    @NotNull @PositiveOrZero Integer displayOrder,
    @NotNull SkuStatus status) {
  public SkuCreateRequest(
      String skuCode,
      String name,
      BigDecimal price,
      Boolean subscribable,
      Integer displayOrder,
      SkuStatus status) {
    this(skuCode, name, price, null, subscribable, displayOrder, status);
  }
}
