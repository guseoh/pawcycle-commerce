package com.pawcycle.backend.catalog.admin.api;

import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Getter;
import lombok.NoArgsConstructor;
@Getter
@NoArgsConstructor
public final class FacetOptionPatchRequest {
  private String value;
  private boolean valuePresent;
  private Integer displayOrder;
  private boolean displayOrderPresent;

  @JsonSetter("value")
  public void readValue(String candidate) {
    value = candidate;
    valuePresent = true;
  }

  @JsonSetter("displayOrder")
  public void readDisplayOrder(Integer candidate) {
    displayOrder = candidate;
    displayOrderPresent = true;
  }
}
