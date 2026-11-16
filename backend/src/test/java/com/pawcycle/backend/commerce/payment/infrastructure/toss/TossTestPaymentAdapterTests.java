package com.pawcycle.backend.commerce.payment.infrastructure.toss;

import com.pawcycle.backend.common.external.ExternalCallFailures;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.pawcycle.backend.commerce.payment.application.TossPaymentAdapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class TossTestPaymentAdapterTests {
  @Test
  void requiresAnExplicitTossTestSecretKeyAndRejectsLiveKeys() {
    assertThatThrownBy(
            () ->
                new TossTestPaymentAdapter(
                    "",
                    "https://api.tosspayments.com",
                    new ObjectMapper(),
                    HttpClient.newHttpClient(),
                    new ExternalCallFailures(new SimpleMeterRegistry())))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                new TossTestPaymentAdapter(
                    "live_sk_not_allowed",
                    "https://api.tosspayments.com",
                    new ObjectMapper(),
                    HttpClient.newHttpClient(),
                    new ExternalCallFailures(new SimpleMeterRegistry())))
        .isInstanceOf(IllegalStateException.class);
    for (String wrongKind : new String[] {"live_gsk_not_allowed", "test_sk_individual_api_key"}) {
      assertThatThrownBy(
              () ->
                  new TossTestPaymentAdapter(
                      wrongKind,
                      "https://api.tosspayments.com",
                      new ObjectMapper(),
                      HttpClient.newHttpClient(),
                    new ExternalCallFailures(new SimpleMeterRegistry())))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Test
  void browserTestCapabilityIsExposedOnlyByTheRealTossTestAdapter() {
    TossPaymentAdapter fake = new TossSandboxPaymentAdapter();
    TossPaymentAdapter actualTest =
        new TossTestPaymentAdapter(
            "test_gsk_example",
            "https://api.tosspayments.com",
            new ObjectMapper(),
            HttpClient.newHttpClient(),
            new ExternalCallFailures(new SimpleMeterRegistry()));

    assertThat(fake.browserTestEnabled()).isFalse();
    assertThat(actualTest.browserTestEnabled()).isTrue();
  }

  @Test
  void sandboxAdapterRemainsTheDefaultWhenTestOptInIsAbsent() {
    TossPaymentAdapter adapter = new TossSandboxPaymentAdapter();
    assertThat(adapter.confirm("payment-key", "order-1", java.math.BigDecimal.TEN).status())
        .isEqualTo("SUCCEEDED");
  }
}
