package com.pawcycle.backend.subscription;

public class SubscriptionApiException extends RuntimeException {

  private final int status;
  private final String code;

  public SubscriptionApiException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public SubscriptionApiException(int status, String code, String message, Throwable cause) {
    super(message, cause);
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
