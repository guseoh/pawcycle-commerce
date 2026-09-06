package com.pawcycle.backend.commerce.coupon.api;

import com.pawcycle.backend.commerce.coupon.application.CouponValidationException;
import com.pawcycle.backend.common.error.ApiErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 쿠폰 feature의 도메인 validation 오류를 HTTP 오류 계약으로 변환한다. */
@RestControllerAdvice(assignableTypes = AdminCouponController.class)
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class CouponExceptionHandler {
  @ExceptionHandler(CouponValidationException.class)
  ResponseEntity<ApiErrorResponse> validation(CouponValidationException exception) {
    return ResponseEntity.badRequest()
        .body(new ApiErrorResponse("VALIDATION_FAILED", exception.getMessage(), exception.fieldErrors()));
  }
}
