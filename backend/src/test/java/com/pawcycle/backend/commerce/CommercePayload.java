package com.pawcycle.backend.commerce;

import java.util.LinkedHashMap;
import java.util.Map;

/** Test facade compatibility shape; production HTTP APIs use named responses. */
final class CommercePayload extends LinkedHashMap<String, Object> {
  private CommercePayload(Map<String, Object> values) {
    super(values);
  }

  static CommercePayload from(Map<String, Object> values) {
    return new CommercePayload(values);
  }
}
