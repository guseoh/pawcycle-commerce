package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TossSandboxBillingAdapterTests {
	private final TossBillingAdapter adapter = new TossSandboxBillingAdapter();

	@Test
	void neverUsesNetworkAndMapsBillingChargeOutcomes() {
		assertThat(adapter.issueBillingKey("customer", "auth").billingKey()).startsWith("sandbox-billing-");
		assertThat(adapter.charge("billing", "order", BigDecimal.ONE).status()).isEqualTo("SUCCEEDED");
		assertThat(adapter.charge("billing", "order-fail", BigDecimal.ONE).status()).isEqualTo("FAILED");
		assertThat(adapter.charge("billing", "order-unknown", BigDecimal.ONE).status()).isEqualTo("UNKNOWN");
		assertThat(adapter.queryCharge("order").status()).isEqualTo("SUCCEEDED");
		assertThat(adapter.queryCharge("order-fail").status()).isEqualTo("FAILED");
		assertThat(adapter.queryCharge("order-unknown").status()).isEqualTo("UNKNOWN");
	}
}
