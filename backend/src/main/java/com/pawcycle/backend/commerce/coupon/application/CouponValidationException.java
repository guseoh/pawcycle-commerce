package com.pawcycle.backend.commerce.coupon.application;

import com.pawcycle.backend.common.error.FieldErrorResponse;
import java.util.List;

/** 쿠폰 PATCH의 필드 및 병합 상태 검증 오류이다. */
public class CouponValidationException extends RuntimeException {
  private final List<FieldErrorResponse> fieldErrors;

  public CouponValidationException(List<FieldErrorResponse> fieldErrors) {
    super("요청 값이 올바르지 않습니다.");
    this.fieldErrors = List.copyOf(fieldErrors);
  }

  public List<FieldErrorResponse> fieldErrors() {
    return fieldErrors;
  }
}
