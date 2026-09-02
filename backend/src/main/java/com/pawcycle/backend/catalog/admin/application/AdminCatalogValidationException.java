package com.pawcycle.backend.catalog.admin.application;

import com.pawcycle.backend.common.error.FieldErrorResponse;
import java.util.List;

public class AdminCatalogValidationException extends RuntimeException {
  private final List<FieldErrorResponse> fieldErrors;

  public AdminCatalogValidationException(List<FieldErrorResponse> fieldErrors) {
    super("Admin catalog request validation failed");
    this.fieldErrors = List.copyOf(fieldErrors);
  }

  public List<FieldErrorResponse> getFieldErrors() {
    return fieldErrors;
  }
}
