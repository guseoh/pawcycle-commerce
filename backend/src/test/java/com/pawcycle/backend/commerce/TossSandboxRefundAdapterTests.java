package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TossSandboxRefundAdapterTests {
	private final TossRefundAdapter adapter = new TossSandboxRefundAdapter();
	@Test void mapsSandboxRefundAndReconciliationWithoutProviderNetworkAccess() {
		assertThat(adapter.refund("refund-ok", BigDecimal.ONE).status()).isEqualTo("SUCCEEDED");
		assertThat(adapter.refund("refund-unknown", BigDecimal.ONE).status()).isEqualTo("UNKNOWN");
		assertThat(adapter.reconcile("refund-ok").status()).isEqualTo("SUCCEEDED");
	}
}
