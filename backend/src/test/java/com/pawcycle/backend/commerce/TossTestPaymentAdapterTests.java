package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.http.HttpClient;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class TossTestPaymentAdapterTests {
	@Test
	void requiresAnExplicitTossTestSecretKeyAndRejectsLiveKeys() {
		assertThatThrownBy(() -> new TossTestPaymentAdapter("", "https://api.tosspayments.com", new ObjectMapper(), HttpClient.newHttpClient()))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new TossTestPaymentAdapter("live_sk_not_allowed", "https://api.tosspayments.com", new ObjectMapper(), HttpClient.newHttpClient()))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void sandboxAdapterRemainsTheDefaultWhenTestOptInIsAbsent() {
		TossPaymentAdapter adapter = new TossSandboxPaymentAdapter();
		org.assertj.core.api.Assertions.assertThat(adapter.confirm("payment-key", "order-1", java.math.BigDecimal.TEN).status()).isEqualTo("SUCCEEDED");
	}
}
