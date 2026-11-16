package com.pawcycle.backend.common.external;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Records an external call failure exactly where the caller turns it into a fallback outcome
 * (UNKNOWN, NO_RESPONSE, an empty AI result). It never changes that outcome.
 *
 * <p>Logs carry only the call, the class, an internal subject reference and the exception type.
 * ERROR entries add the stack trace with every exception message removed ({@link RedactedFailure}).
 * Provider response bodies, payment keys and personal data must never be passed as the subject.
 */
@Component
public class ExternalCallFailures {
  static final String METRIC = "pawcycle.external.call.failures";
  private static final Logger log = LoggerFactory.getLogger(ExternalCallFailures.class);
  private static final String MESSAGE =
      "External call failed; provider={}, operation={}, failureClass={}, subject={}, cause={}";

  private final Map<ExternalCall, Map<FailureClass, Counter>> counters =
      new EnumMap<>(ExternalCall.class);

  public ExternalCallFailures(MeterRegistry registry) {
    // Register every combination up front so the first failure is visible to increase()/rate().
    for (ExternalCall call : ExternalCall.values()) {
      Map<FailureClass, Counter> byClass = new EnumMap<>(FailureClass.class);
      for (FailureClass failureClass : FailureClass.values()) {
        byClass.put(
            failureClass,
            Counter.builder(METRIC)
                .tag("provider", call.provider())
                .tag("operation", call.operation())
                .tag("failure_class", failureClass.tag())
                .register(registry));
      }
      counters.put(call, byClass);
    }
  }

  /** Classifies, counts and logs a caught failure. ERROR classes include the stack trace. */
  public FailureClass record(ExternalCall call, Throwable failure, String subject) {
    FailureClass failureClass = ExternalFailureClassifier.classify(failure);
    counters.get(call).get(failureClass).increment();
    String type = failure.getClass().getSimpleName();
    if (failureClass.error()) {
      // Types and frames only: exception messages may quote provider response text.
      log.error(
          MESSAGE, call.provider(), call.operation(), failureClass, subject, type,
          RedactedFailure.of(failure));
    } else {
      log.warn(MESSAGE, call.provider(), call.operation(), failureClass, subject, type);
    }
    return failureClass;
  }

  /** Counts and logs a failure observed without an exception, such as an HTTP status. */
  public void record(ExternalCall call, FailureClass failureClass, String subject, String cause) {
    counters.get(call).get(failureClass).increment();
    if (failureClass.error()) {
      log.error(MESSAGE, call.provider(), call.operation(), failureClass, subject, cause);
    } else {
      log.warn(MESSAGE, call.provider(), call.operation(), failureClass, subject, cause);
    }
  }
}
