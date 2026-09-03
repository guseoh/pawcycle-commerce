package com.pawcycle.backend.commerce;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import java.util.List;
import java.util.Map;

/** Typed HTTP envelope for a legacy-shaped resource row. */
public record CommerceRowResponse(Map<String, Object> values) {
  public static List<CommerceRowResponse> from(List<Map<String, Object>> rows) {
    return rows.stream().map(CommerceRowResponse::new).toList();
  }

  @JsonAnyGetter
  public Map<String, Object> jsonValues() {
    return values;
  }
}
