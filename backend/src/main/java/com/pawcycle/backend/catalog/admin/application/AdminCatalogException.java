package com.pawcycle.backend.catalog.admin.application;

public class AdminCatalogException extends RuntimeException {
  private final String code;

  public AdminCatalogException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
