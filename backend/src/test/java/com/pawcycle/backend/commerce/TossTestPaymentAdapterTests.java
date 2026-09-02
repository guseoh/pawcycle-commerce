package com.pawcycle.backend.commerce;

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
                    HttpClient.newHttpClient()))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                new TossTestPaymentAdapter(
                    "live_sk_not_allowed",
                    "https://api.tosspayments.com",
                    new ObjectMapper(),
                    HttpClient.newHttpClient()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void browserTestCapabilityIsExposedOnlyByTheRealTossTestAdapter() {
    TossPaymentAdapter fake = new TossSandboxPaymentAdapter();
    TossPaymentAdapter actualTest =
        new TossTestPaymentAdapter(
            "test_sk_example",
            "https://api.tosspayments.com",
            new ObjectMapper(),
            HttpClient.newHttpClient());

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
