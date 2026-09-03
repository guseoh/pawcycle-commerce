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
public final class ProductPatchRequest {
  private Long categoryId;
  private boolean categoryIdPresent;
  private Long brandId;
  private boolean brandIdPresent;
  private String name;
  private boolean namePresent;
  private String shortDescription;
  private boolean shortDescriptionPresent;
  private String description;
  private boolean descriptionPresent;
  private String petType;
  private boolean petTypePresent;
  private String thumbnailUrl;
  private boolean thumbnailUrlPresent;
  private ProductStatus status;
  private boolean statusPresent;

  @JsonSetter("categoryId")
  public void readCategoryId(Long value) {
    categoryId = value;
    categoryIdPresent = true;
  }

  @JsonSetter("brandId")
  public void readBrandId(Long value) {
    brandId = value;
    brandIdPresent = true;
  }

  @JsonSetter("name")
  public void readName(String value) {
    name = value;
    namePresent = true;
  }

  @JsonSetter("shortDescription")
  public void readShortDescription(String value) {
    shortDescription = value;
    shortDescriptionPresent = true;
  }

  @JsonSetter("description")
  public void readDescription(String value) {
    description = value;
    descriptionPresent = true;
  }

  @JsonSetter("petType")
  public void readPetType(String value) {
    petType = value;
    petTypePresent = true;
  }

  @JsonSetter("thumbnailUrl")
  public void readThumbnailUrl(String value) {
    thumbnailUrl = value;
    thumbnailUrlPresent = true;
  }

  @JsonSetter("status")
  public void readStatus(ProductStatus value) {
    status = value;
    statusPresent = true;
  }
}
