package com.pawcycle.backend.member.address.api;

import com.pawcycle.backend.commerce.CommerceException;
import com.pawcycle.backend.common.error.ApiErrorResponse;
import com.pawcycle.backend.member.address.application.MemberAddressException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 회원 배송지 유스케이스 오류를 배송지 전용 오류 계약으로 변환한다. */
@RestControllerAdvice(assignableTypes = MemberAddressController.class)
public class MemberAddressExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(MemberAddressExceptionHandler.class);

  @ExceptionHandler(MemberAddressException.class)
  ResponseEntity<ApiErrorResponse> handle(MemberAddressException exception) {
    return ResponseEntity.status(exception.status())
        .body(ApiErrorResponse.withoutFieldErrors(exception.code(), exception.getMessage()));
  }

  @ExceptionHandler(CommerceException.class)
  ResponseEntity<ApiErrorResponse> handleCommerce(CommerceException exception) {
    return ResponseEntity.status(exception.status())
        .body(ApiErrorResponse.withoutFieldErrors(exception.code(), exception.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiErrorResponse> unexpected(Exception exception) {
    log.error("Unexpected exception while processing member address request", exception);
    return ResponseEntity.internalServerError()
        .body(ApiErrorResponse.withoutFieldErrors(
            "MEMBER_ADDRESS_UNAVAILABLE", "배송지 요청을 처리하지 못했습니다."));
  }
}
