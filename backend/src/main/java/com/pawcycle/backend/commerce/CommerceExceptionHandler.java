package com.pawcycle.backend.commerce;

import com.pawcycle.backend.common.error.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.pawcycle.backend.commerce")
public class CommerceExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(CommerceExceptionHandler.class);

  @ExceptionHandler(CommerceException.class)
  ResponseEntity<ApiErrorResponse> commerce(CommerceException exception) {
    return ResponseEntity.status(exception.status())
        .body(ApiErrorResponse.withoutFieldErrors(exception.code(), exception.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiErrorResponse> unexpected(Exception exception) {
    log.error("Unexpected exception while processing commerce request", exception);
    return ResponseEntity.internalServerError()
        .body(ApiErrorResponse.withoutFieldErrors("INTERNAL_ERROR", "요청을 처리할 수 없습니다."));
  }
}
