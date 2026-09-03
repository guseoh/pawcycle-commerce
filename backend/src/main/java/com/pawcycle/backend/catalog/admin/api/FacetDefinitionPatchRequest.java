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
@Getter
@NoArgsConstructor
public final class FacetDefinitionPatchRequest {
  private String key;
  private boolean keyPresent;
  private String name;
  private boolean namePresent;

  @JsonSetter("key")
  public void readKey(String value) {
    key = value;
    keyPresent = true;
  }

  @JsonSetter("name")
  public void readName(String value) {
    name = value;
    namePresent = true;
  }
}
