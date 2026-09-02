package com.pawcycle.backend.catalog.engagement.application;

public class ProductEngagementException extends RuntimeException {
  private final int status;
  private final String code;

  public ProductEngagementException(int status, String code, String message) {
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
