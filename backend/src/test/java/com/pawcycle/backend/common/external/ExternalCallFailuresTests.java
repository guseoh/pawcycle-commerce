package com.pawcycle.backend.common.external;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.ConnectException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class ExternalCallFailuresTests {
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final ExternalCallFailures failures = new ExternalCallFailures(registry);

  @Test
  void everyCallAndClassIsRegisteredAtZeroSoTheFirstFailureIsVisible() {
    var counters = registry.get(ExternalCallFailures.METRIC).counters();
    assertThat(counters)
        .hasSize(ExternalCall.values().length * FailureClass.values().length)
        .allSatisfy(counter -> assertThat(counter.count()).isZero());
  }

  @Test
  void providerFailureIsCountedAndLoggedAsWarningWithoutStackTrace(CapturedOutput output) {
    FailureClass result =
        failures.record(ExternalCall.TOSS_PAYMENT_CONFIRM, new ConnectException("refused"), "paymentId=7");

    assertThat(result).isEqualTo(FailureClass.CONNECT);
    assertThat(count("payment.confirm", "connect")).isEqualTo(1.0);
    assertThat(output.getOut())
        .contains("WARN")
        .contains("provider=toss, operation=payment.confirm, failureClass=CONNECT")
        .contains("subject=paymentId=7, cause=ConnectException")
        .doesNotContain("\tat ");
  }

  @Test
  void internalDefectIsLoggedAsErrorWithStackTrace(CapturedOutput output) {
    failures.record(ExternalCall.OPENAI_RECOMMENDATION, new NullPointerException("bug"), "petId=3");

    assertThat(count("recommendation", "internal")).isEqualTo(1.0);
    assertThat(output.getOut())
        .contains("ERROR")
        .contains("failureClass=INTERNAL")
        .contains("java.lang.NullPointerException: bug")
        .contains("\tat ");
  }

  @Test
  void statusOnlyFailureIsCounted(CapturedOutput output) {
    failures.record(ExternalCall.TOSS_PAYMENT_QUERY, FailureClass.PROVIDER_5XX, "providerOrderId=o-1", "HTTP 502");

    assertThat(count("payment.query", "provider_5xx")).isEqualTo(1.0);
    assertThat(output.getOut()).contains("failureClass=PROVIDER_5XX").contains("cause=HTTP 502");
  }

  private double count(String operation, String failureClass) {
    Counter counter = registry.get(ExternalCallFailures.METRIC)
        .tag("operation", operation)
        .tag("failure_class", failureClass)
        .counter();
    return counter.count();
  }
}
