package com.pawcycle.backend.common.external;

/**
 * Thrown by a provider adapter when it already knows why the call failed, so the caller does not
 * have to guess from a wrapped cause. The message never contains provider response bodies.
 */
public class ExternalCallException extends RuntimeException {
  private final FailureClass failureClass;

  public ExternalCallException(FailureClass failureClass, String message) {
    super(message);
    this.failureClass = failureClass;
  }

  public ExternalCallException(FailureClass failureClass, Throwable cause) {
    super(failureClass.name(), cause);
    this.failureClass = failureClass;
  }

  /** Keeps an existing classification, otherwise classifies {@code failure}. */
  public static ExternalCallException of(Throwable failure) {
    if (failure instanceof ExternalCallException classified) return classified;
    return new ExternalCallException(ExternalFailureClassifier.classify(failure), failure);
  }

  public FailureClass failureClass() {
    return failureClass;
  }
}
