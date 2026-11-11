package com.pawcycle.backend.catalog.admin.api;

import com.fasterxml.jackson.annotation.JsonSetter;
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
