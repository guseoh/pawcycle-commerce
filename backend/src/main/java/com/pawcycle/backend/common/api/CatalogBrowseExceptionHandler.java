package com.pawcycle.backend.common.api;

import com.pawcycle.backend.common.error.ApiErrorResponse;
import com.pawcycle.backend.common.error.UnexpectedErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Catch-all for the public category and discovery endpoints, which had no advice (stage 2, O11). */
@RestControllerAdvice(basePackages = {
  "com.pawcycle.backend.catalog.category.api",
  "com.pawcycle.backend.catalog.discovery.api"
})
class CatalogBrowseExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(CatalogBrowseExceptionHandler.class);

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiErrorResponse> unexpected(Exception exception) throws Exception {
    return UnexpectedErrors.internalError(log, "catalog-browse", exception);
  }
}
