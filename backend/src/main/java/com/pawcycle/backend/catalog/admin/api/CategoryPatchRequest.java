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
public final class CategoryPatchRequest {
  private String name;
  private boolean namePresent;
  private String slug;
  private boolean slugPresent;
  private Integer displayOrder;
  private boolean displayOrderPresent;
  private Boolean active;
  private boolean activePresent;
  private Long parentId;
  private boolean parentIdPresent;

  @JsonSetter("name")
  public void readName(String value) {
    name = value;
    namePresent = true;
  }

  @JsonSetter("slug")
  public void readSlug(String value) {
    slug = value;
    slugPresent = true;
  }

  @JsonSetter("displayOrder")
  public void readDisplayOrder(Integer value) {
    displayOrder = value;
    displayOrderPresent = true;
  }

  @JsonSetter("active")
  public void readActive(Boolean value) {
    active = value;
    activePresent = true;
  }

  @JsonSetter("parentId")
  public void readParentId(Long value) {
    parentId = value;
    parentIdPresent = true;
  }
}
