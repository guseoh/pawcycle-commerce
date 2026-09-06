package com.pawcycle.backend.subscription;

import com.pawcycle.backend.common.error.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
    assignableTypes = {SubscriptionController.class, RepeatCommerceController.class})
public class SubscriptionExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(SubscriptionExceptionHandler.class);

  @ExceptionHandler(SubscriptionApiException.class)
  ResponseEntity<ApiErrorResponse> handleSubscriptionError(SubscriptionApiException error) {
    return ResponseEntity.status(error.status())
        .body(ApiErrorResponse.withoutFieldErrors(error.code(), error.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiErrorResponse> unexpected(Exception exception) {
    log.error("Unexpected exception while processing subscription request", exception);
    return ResponseEntity.internalServerError()
        .body(ApiErrorResponse.withoutFieldErrors("INTERNAL_ERROR", "요청을 처리할 수 없습니다."));
  }
}
