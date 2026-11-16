package com.pawcycle.backend.catalog.product.api;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.pawcycle.backend.common.error.UnexpectedErrors;
import com.pawcycle.backend.catalog.product.application.ProductComparisonException;
import com.pawcycle.backend.common.error.ApiErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ProductComparisonController.class)
class ProductComparisonExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ProductComparisonExceptionHandler.class);

  @ExceptionHandler(ProductComparisonException.class)
  ResponseEntity<ApiErrorResponse> handle(ProductComparisonException e) {
    return ResponseEntity.status(e.status())
        .body(ApiErrorResponse.withoutFieldErrors(e.code(), e.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiErrorResponse> unexpected(Exception exception) throws Exception {
    return UnexpectedErrors.internalError(log, "product-comparison", exception);
  }
}
