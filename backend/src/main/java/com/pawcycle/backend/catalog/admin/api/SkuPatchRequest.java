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
public final class SkuPatchRequest {
  private String name;
  private boolean namePresent;
  private BigDecimal price;
  private boolean pricePresent;
  private BigDecimal compareAtPrice;
  private boolean compareAtPricePresent;
  private Boolean subscribable;
  private boolean subscribablePresent;
  private Integer displayOrder;
  private boolean displayOrderPresent;
  private SkuStatus status;
  private boolean statusPresent;

  @JsonSetter("name")
  public void readName(String value) {
    name = value;
    namePresent = true;
  }

  @JsonSetter("price")
  public void readPrice(BigDecimal value) {
    price = value;
    pricePresent = true;
  }

  @JsonSetter("compareAtPrice")
  public void readCompareAtPrice(BigDecimal value) {
    compareAtPrice = value;
    compareAtPricePresent = true;
  }

  @JsonSetter("subscribable")
  public void readSubscribable(Boolean value) {
    subscribable = value;
    subscribablePresent = true;
  }

  @JsonSetter("displayOrder")
  public void readDisplayOrder(Integer value) {
    displayOrder = value;
    displayOrderPresent = true;
  }

  @JsonSetter("status")
  public void readStatus(SkuStatus value) {
    status = value;
    statusPresent = true;
  }
}
