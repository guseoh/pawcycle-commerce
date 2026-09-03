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
public final class DetailSectionPatchRequest {
  private String title;
  private boolean titlePresent;
  private String body;
  private boolean bodyPresent;
  private Integer displayOrder;
  private boolean displayOrderPresent;
  private Boolean visible;
  private boolean visiblePresent;

  @JsonSetter("title")
  public void readTitle(String value) {
    title = value;
    titlePresent = true;
  }

  @JsonSetter("body")
  public void readBody(String value) {
    body = value;
    bodyPresent = true;
  }

  @JsonSetter("displayOrder")
  public void readDisplayOrder(Integer value) {
    displayOrder = value;
    displayOrderPresent = true;
  }

  @JsonSetter("visible")
  public void readVisible(Boolean value) {
    visible = value;
    visiblePresent = true;
  }
}
