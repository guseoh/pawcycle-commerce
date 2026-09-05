package com.pawcycle.backend.commerce;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import java.util.Map;

/** Test facade compatibility shape; production operations use a named response. */
record CommerceRowResponse(Map<String, Object> values) {
  @JsonAnyGetter
  public Map<String, Object> jsonValues() {
    return values;
  }
}
