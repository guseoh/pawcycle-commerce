package com.pawcycle.backend.recommendation;

import com.pawcycle.backend.common.error.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
    assignableTypes = {RecommendationController.class, ProductRecommendationController.class})
class RecommendationExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(RecommendationExceptionHandler.class);

  @ExceptionHandler(RecommendationException.class)
  ResponseEntity<ApiErrorResponse> handleRecommendation(RecommendationException exception) {
    return ResponseEntity.status(exception.status())
        .body(ApiErrorResponse.withoutFieldErrors(exception.code(), exception.getMessage()));
  }

  @ExceptionHandler(RecommendationPetNotFoundException.class)
  ResponseEntity<ApiErrorResponse> petNotFound() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiErrorResponse.withoutFieldErrors("PET_NOT_FOUND", "Pet을 찾을 수 없습니다."));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiErrorResponse> unexpected(Exception exception) {
    log.error("Unexpected exception while processing recommendation request", exception);
    return ResponseEntity.internalServerError()
        .body(ApiErrorResponse.withoutFieldErrors("INTERNAL_ERROR", "요청을 처리할 수 없습니다."));
  }
}
