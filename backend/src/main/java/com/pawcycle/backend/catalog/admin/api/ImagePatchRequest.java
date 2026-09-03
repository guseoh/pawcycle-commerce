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
public final class ImagePatchRequest {
  private String imageUrl;
  private boolean imageUrlPresent;
  private String altText;
  private boolean altTextPresent;
  private Integer displayOrder;
  private boolean displayOrderPresent;
  private String imageType;
  private boolean imageTypePresent;

  @JsonSetter("imageUrl")
  public void readImageUrl(String value) {
    imageUrl = value;
    imageUrlPresent = true;
  }

  @JsonSetter("altText")
  public void readAltText(String value) {
    altText = value;
    altTextPresent = true;
  }

  @JsonSetter("displayOrder")
  public void readDisplayOrder(Integer value) {
    displayOrder = value;
    displayOrderPresent = true;
  }

  @JsonSetter("imageType")
  public void readImageType(String value) {
    imageType = value;
    imageTypePresent = true;
  }
}
