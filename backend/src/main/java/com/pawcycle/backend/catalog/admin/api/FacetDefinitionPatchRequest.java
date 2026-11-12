package com.pawcycle.backend.catalog.admin.api;

import com.fasterxml.jackson.annotation.JsonSetter;
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
