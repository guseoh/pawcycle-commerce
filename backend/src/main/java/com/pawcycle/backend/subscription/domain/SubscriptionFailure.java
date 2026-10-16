package com.pawcycle.backend.subscription.domain;

/** Internal subscription failure; the API boundary owns its transport representation. */
public class SubscriptionFailure extends RuntimeException {
  public enum Kind {
    BAD_REQUEST,
    NOT_FOUND,
    CONFLICT,
    PRECONDITION_FAILED,
    PRECONDITION_REQUIRED
  }

  private final Kind kind;
  private final String code;

  public SubscriptionFailure(Kind kind, String code, String message) {
    super(message);
    this.kind = kind;
    this.code = code;
  }

  public SubscriptionFailure(Kind kind, String code, String message, Throwable cause) {
    super(message, cause);
    this.kind = kind;
    this.code = code;
  }

  public Kind kind() {
    return kind;
  }

  public String code() {
    return code;
  }
}
