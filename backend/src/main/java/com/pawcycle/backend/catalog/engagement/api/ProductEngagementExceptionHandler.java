package com.pawcycle.backend.catalog.engagement.api;

import com.pawcycle.backend.catalog.engagement.application.ProductEngagementException;
import com.pawcycle.backend.common.error.ApiErrorResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
    assignableTypes = {
      ProductReviewController.class,
      ProductQuestionController.class,
      AdminProductEngagementController.class
    })
public class ProductEngagementExceptionHandler {
  private static final Logger log =
      LoggerFactory.getLogger(ProductEngagementExceptionHandler.class);

  @ExceptionHandler(ProductEngagementException.class)
  ResponseEntity<ApiErrorResponse> engagement(ProductEngagementException exception) {
    return ResponseEntity.status(exception.status())
        .body(ApiErrorResponse.withoutFieldErrors(exception.code(), exception.getMessage()));
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
  ResponseEntity<ApiErrorResponse> validation(Exception exception) {
    return ResponseEntity.badRequest()
        .body(new ApiErrorResponse("VALIDATION_FAILED", "요청 값을 확인해 주세요.", List.of()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiErrorResponse> unexpected(Exception exception) {
    log.error("Unexpected exception while processing product engagement request", exception);
    return ResponseEntity.internalServerError()
        .body(ApiErrorResponse.withoutFieldErrors("INTERNAL_ERROR", "요청을 처리할 수 없습니다."));
  }
}
