package com.pawcycle.backend.catalog.product.application;

public class ProductComparisonException extends RuntimeException {
  private final int status;
  private final String code;

  public ProductComparisonException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }
}
