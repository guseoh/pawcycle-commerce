package com.pawcycle.backend.subscription.api;

import com.pawcycle.backend.common.error.ApiErrorResponse;
import com.pawcycle.backend.subscription.domain.SubscriptionFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
    assignableTypes = {SubscriptionController.class, RepeatCommerceController.class})
public class SubscriptionExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(SubscriptionExceptionHandler.class);

  @ExceptionHandler(SubscriptionFailure.class)
  ResponseEntity<ApiErrorResponse> handleSubscriptionFailure(SubscriptionFailure error) {
    int status = switch (error.kind()) {
      case BAD_REQUEST -> 400;
      case NOT_FOUND -> 404;
      case CONFLICT -> 409;
      case PRECONDITION_FAILED -> 412;
      case PRECONDITION_REQUIRED -> 428;
    };
    return ResponseEntity.status(status)
        .body(ApiErrorResponse.withoutFieldErrors(error.code(), error.getMessage()));
  }

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
