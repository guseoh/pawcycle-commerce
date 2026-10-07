package com.pawcycle.backend.recommendation.application;

public class RecommendationException extends RuntimeException {
  private final int status;
  private final String code;

  public RecommendationException(int status, String code, String message) {
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
