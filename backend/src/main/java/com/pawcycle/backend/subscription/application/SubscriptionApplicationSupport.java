package com.pawcycle.backend.subscription.application;

import com.pawcycle.backend.subscription.domain.SubscriptionFailure;

import com.pawcycle.backend.subscription.api.SubscriptionDetailResponse;
import com.pawcycle.backend.subscription.model.SubscriptionReadModel;
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
    if (value == null) throw new SubscriptionFailure(SubscriptionFailure.Kind.PRECONDITION_REQUIRED, "IF_MATCH_REQUIRED", "If-Match가 필요합니다.");
    if (!value.matches("\\\"[0-9]+\\\""))
      throw new SubscriptionFailure(SubscriptionFailure.Kind.BAD_REQUEST, "IF_MATCH_INVALID", "If-Match 형식이 올바르지 않습니다.");
    try {
      return Long.parseLong(value.substring(1, value.length() - 1));
    } catch (NumberFormatException exception) {
      throw new SubscriptionFailure(
          SubscriptionFailure.Kind.BAD_REQUEST, "IF_MATCH_INVALID", "If-Match 형식이 올바르지 않습니다.", exception);
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

  SubscriptionDetailResponse detailResponse(SubscriptionReadModel.Detail detail) {
    return SubscriptionDetailResponse.from(detail);
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

  SubscriptionFailure validation(String field) {
    return new SubscriptionFailure(SubscriptionFailure.Kind.BAD_REQUEST, "VALIDATION_FAILED", field + " 값을 확인해 주세요.");
  }

  SubscriptionFailure state() {
    return new SubscriptionFailure(
        SubscriptionFailure.Kind.CONFLICT, "SUBSCRIPTION_COMMAND_NOT_ALLOWED", "현재 Subscription 상태에서는 명령을 실행할 수 없습니다.");
  }
}
