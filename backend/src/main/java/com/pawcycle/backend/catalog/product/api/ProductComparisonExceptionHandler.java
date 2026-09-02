package com.pawcycle.backend.catalog.product.api;

import com.pawcycle.backend.catalog.product.application.ProductComparisonException;
import com.pawcycle.backend.common.error.ApiErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ProductComparisonController.class)
class ProductComparisonExceptionHandler {
  @ExceptionHandler(ProductComparisonException.class)
  ResponseEntity<ApiErrorResponse> handle(ProductComparisonException e) {
    return ResponseEntity.status(e.status())
        .body(ApiErrorResponse.withoutFieldErrors(e.code(), e.getMessage()));
  }
}
