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
        .contains("NullPointerException")
        .doesNotContain(": bug")
        .contains("\tat ");
  }

  @Test
  void statusOnlyFailureIsCounted(CapturedOutput output) {
    failures.record(ExternalCall.TOSS_PAYMENT_QUERY, FailureClass.PROVIDER_5XX, "providerOrderId=o-1", "HTTP 502");

    assertThat(count("payment.query", "provider_5xx")).isEqualTo(1.0);
    assertThat(output.getOut()).contains("failureClass=PROVIDER_5XX").contains("cause=HTTP 502");
  }

  @Test
  void errorLogKeepsTheStackButNeverTheProviderTextInExceptionMessages(CapturedOutput output) {
    // A provider body with an unquoted key makes Jackson echo the token in its message.
    RuntimeException parse = parseFailure("{\"paymentKey\": tgen_SECRET_PAYMENT_KEY_1}");
    assertThat(parse.getMessage()).contains("tgen_SECRET_PAYMENT_KEY_1");
    RuntimeException wrapped = ExternalCallException.of(parse);

    failures.record(ExternalCall.TOSS_PAYMENT_CONFIRM, wrapped, "paymentId=7");

    assertThat(count("payment.confirm", "parse")).isEqualTo(1.0);
    assertThat(output.getOut())
        .contains("ERROR")
        .contains("failureClass=PARSE")
        .contains("ExternalCallException")
        .contains("StreamReadException")
        .contains("	at ")
        .doesNotContain("tgen_SECRET_PAYMENT_KEY_1");
  }

  @Test
  void internalDefectMessageIsAlsoRemoved(CapturedOutput output) {
    failures.record(
        ExternalCall.OPENAI_REVIEW_SUMMARY,
        new IllegalStateException("customer email a@example.com"),
        "productId=1");

    assertThat(output.getOut())
        .contains("IllegalStateException")
        .contains("	at ")
        .doesNotContain("a@example.com");
  }

  private static RuntimeException parseFailure(String body) {
    try {
      new tools.jackson.databind.ObjectMapper().readTree(body);
      throw new AssertionError("expected a parse failure");
    } catch (tools.jackson.core.JacksonException expected) {
      return expected;
    }
  }

  private double count(String operation, String failureClass) {
    Counter counter = registry.get(ExternalCallFailures.METRIC)
        .tag("operation", operation)
        .tag("failure_class", failureClass)
        .counter();
    return counter.count();
  }
}
