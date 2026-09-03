package com.pawcycle.backend.subscription;

import com.pawcycle.backend.subscription.api.SubscriptionDetailResponse;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import tools.jackson.databind.ObjectMapper;

final class SubscriptionApplicationSupport {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private final ObjectMapper json;
  private final Clock clock;

  SubscriptionApplicationSupport(ObjectMapper json, Clock clock) {
    this.json = json;
    this.clock = clock;
  }

  void validateKey(String key) {
    if (key == null || !key.matches("[A-Za-z0-9._-]{1,128}")) throw validation("Idempotency-Key");
  }

  long parseEtag(String value) {
    if (value == null) throw new SubscriptionApiException(428, "IF_MATCH_REQUIRED", "If-Match가 필요합니다.");
    if (!value.matches("\\\"[0-9]+\\\""))
      throw new SubscriptionApiException(400, "IF_MATCH_INVALID", "If-Match 형식이 올바르지 않습니다.");
    try {
      return Long.parseLong(value.substring(1, value.length() - 1));
    } catch (NumberFormatException exception) {
      throw new SubscriptionApiException(
          400, "IF_MATCH_INVALID", "If-Match 형식이 올바르지 않습니다.", exception);
    }
  }

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

  SubscriptionDetailResponse responseBody(String bodyJson) {
    try {
      return json.readValue(bodyJson, SubscriptionDetailResponse.class);
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

  long requiredLong(Number value, String field) {
    if (value == null) throw validation(field);
    try {
      return new BigDecimal(value.toString()).longValueExact();
    } catch (NumberFormatException | ArithmeticException exception) {
      throw validation(field);
    }
  }

  int requiredInt(Number value, String field) {
    long number = requiredLong(value, field);
    if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) throw validation(field);
    return (int) number;
  }

  LocalDate requiredDate(LocalDate value, String field) {
    if (value == null) throw validation(field);
    return value;
  }

  LocalDate today() {
    return LocalDate.now(clock.withZone(SEOUL));
  }

  SubscriptionApiException validation(String field) {
    return new SubscriptionApiException(400, "VALIDATION_FAILED", field + " 값을 확인해 주세요.");
  }

  SubscriptionApiException state() {
    return new SubscriptionApiException(
        409, "SUBSCRIPTION_COMMAND_NOT_ALLOWED", "현재 Subscription 상태에서는 명령을 실행할 수 없습니다.");
  }
}
