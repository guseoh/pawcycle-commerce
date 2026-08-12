package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TossSandboxPaymentAdapterTests {
	private final TossPaymentAdapter adapter = new TossSandboxPaymentAdapter();

	@Test
	void mapsDeterministicSandboxOutcomesWithoutProviderNetworkAccess() {
		assertThat(adapter.confirm("payment-key", "order-1", BigDecimal.valueOf(100)).status()).isEqualTo("SUCCEEDED");
		assertThat(adapter.confirm("fail_test", "order-1", BigDecimal.valueOf(100)).status()).isEqualTo("FAILED");
		assertThat(adapter.confirm("unknown_test", "order-1", BigDecimal.valueOf(100)).status()).isEqualTo("UNKNOWN");
	}
}
