package com.pawcycle.backend.subscription.application;

import com.pawcycle.backend.subscription.model.SubscriptionReadModel;
import java.security.MessageDigest;
import tools.jackson.databind.ObjectMapper;

/** Exact legacy request fingerprint and durable replay representation. */
final class SubscriptionIdempotencyCodec {
  private final ObjectMapper json;
  SubscriptionIdempotencyCodec(ObjectMapper json) { this.json = json; }

  String fingerprint(Object request) {
    try {
      byte[] bytes = MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(request));
      StringBuilder out = new StringBuilder(64);
      for (byte value : bytes) out.append(String.format("%02x", value));
      return out.toString();
    } catch (Exception exception) {
      throw new IllegalStateException("요청 지문을 계산할 수 없습니다.", exception);
    }
  }

  SubscriptionReadModel.Detail responseBody(String bodyJson) {
    try {
      // The existing model preserves the legacy stored API JSON schema; boundary tests pin its bytes.
      return json.readValue(bodyJson, SubscriptionReadModel.Detail.class);
    } catch (Exception exception) {
      throw new IllegalStateException("저장된 멱등 결과를 읽을 수 없습니다.", exception);
    }
  }

  String bodyJson(Object body) {
    try {
      return json.writeValueAsString(body);
    } catch (Exception exception) {
      throw new IllegalStateException("응답을 저장할 수 없습니다.", exception);
    }
  }

}
