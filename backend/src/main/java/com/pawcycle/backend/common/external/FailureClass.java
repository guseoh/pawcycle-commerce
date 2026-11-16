package com.pawcycle.backend.common.external;

import java.util.Locale;

/**
 * Why an external provider call did not produce a usable answer. The class only drives logs and
 * metrics; callers keep their existing state decisions (for example, a payment stays UNKNOWN).
 */
public enum FailureClass {
  /** The call did not finish in time, or was interrupted while waiting. */
  TIMEOUT(false),
  /** The provider could not be reached (DNS, refused or reset connection, other network I/O). */
  CONNECT(false),
  /** The provider answered with a server error (5xx). */
  PROVIDER_5XX(false),
  /** The provider rejected the request (4xx, including rate limiting). */
  PROVIDER_REJECTED(false),
  /** The provider answered, but the response could not be read. */
  PARSE(true),
  /** Anything else: most likely a defect in our own code. */
  INTERNAL(true);

  private final boolean error;

  FailureClass(boolean error) {
    this.error = error;
  }

  /** True when the failure needs a person: logged at ERROR with the stack trace. */
  public boolean error() {
    return error;
  }

  /** Metric tag value. */
  public String tag() {
    return name().toLowerCase(Locale.ROOT);
  }
}
