package com.pawcycle.backend.catalog.admin.application;

public class AdminCatalogNotFoundException extends AdminCatalogException {
  public AdminCatalogNotFoundException(String code, String message) {
    super(code, message);
  }
}
