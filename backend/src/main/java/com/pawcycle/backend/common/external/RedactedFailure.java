package com.pawcycle.backend.common.external;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * A log-only copy of a failure that keeps each exception type and stack frame but drops every
 * message. Exception messages can echo provider response text (for example a JSON parser quoting an
 * unexpected token such as a payment key), so they are never logged for external calls.
 */
final class RedactedFailure extends RuntimeException {
  private static final int MAX_CAUSES = 16;

  private RedactedFailure(Throwable original, Throwable cause) {
    super(original.getClass().getName() + " (message removed)", cause, false, true);
    setStackTrace(original.getStackTrace());
  }

  static RedactedFailure of(Throwable failure) {
    return copy(failure, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
  }

  private static RedactedFailure copy(Throwable failure, Set<Throwable> seen, int depth) {
    seen.add(failure);
    Throwable cause = failure.getCause();
    RedactedFailure redactedCause =
        cause == null || depth >= MAX_CAUSES || seen.contains(cause)
            ? null
            : copy(cause, seen, depth + 1);
    return new RedactedFailure(failure, redactedCause);
  }
}
