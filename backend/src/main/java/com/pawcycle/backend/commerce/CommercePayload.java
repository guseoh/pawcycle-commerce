package com.pawcycle.backend.commerce;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stable JSON payload type for legacy-shaped commerce responses.
 *
 * <p>The payload keeps the wire shape while feature-specific response records are introduced.
 * New application APIs must expose a named response type instead of a raw map.
 */
public final class CommercePayload extends LinkedHashMap<String, Object> {
  public CommercePayload() {}

  private CommercePayload(Map<String, Object> values) {
    super(values);
  }

  public static CommercePayload from(Map<String, Object> values) {
    return new CommercePayload(values);
  }
}
