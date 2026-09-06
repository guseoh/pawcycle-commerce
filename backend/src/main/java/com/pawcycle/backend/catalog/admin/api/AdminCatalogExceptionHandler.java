package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.admin.application.AdminCatalogConflictException;
import com.pawcycle.backend.catalog.admin.application.AdminCatalogNotFoundException;
import com.pawcycle.backend.catalog.admin.application.AdminCatalogValidationException;
import com.pawcycle.backend.common.error.ApiErrorResponse;
import com.pawcycle.backend.common.error.FieldErrorResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AdminCatalogController.class)
public class AdminCatalogExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(AdminCatalogExceptionHandler.class);

  @ExceptionHandler(AdminCatalogValidationException.class)
  ResponseEntity<ApiErrorResponse> handlePatchValidation(
      AdminCatalogValidationException exception) {
    return validation(exception.getFieldErrors());
  }

  @ExceptionHandler(AdminCatalogNotFoundException.class)
  ResponseEntity<ApiErrorResponse> handleNotFound(AdminCatalogNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiErrorResponse.withoutFieldErrors(exception.getCode(), exception.getMessage()));
  }

  @ExceptionHandler(AdminCatalogConflictException.class)
  ResponseEntity<ApiErrorResponse> handleConflict(AdminCatalogConflictException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiErrorResponse.withoutFieldErrors(exception.getCode(), exception.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
    log.error("Unexpected exception while processing admin catalog request", exception);
    return ResponseEntity.internalServerError()
        .body(
            ApiErrorResponse.withoutFieldErrors(
                "ADMIN_CATALOG_UNAVAILABLE", "관리자 Catalog 요청을 처리하지 못했습니다."));
  }

  private ResponseEntity<ApiErrorResponse> validation(List<FieldErrorResponse> fieldErrors) {
    return ResponseEntity.badRequest()
        .body(new ApiErrorResponse("VALIDATION_FAILED", "요청 값이 올바르지 않습니다.", fieldErrors));
  }
}
