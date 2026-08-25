package com.pawcycle.backend.commerce;

import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local-integration")
@ConditionalOnProperty(name = "pawcycle.toss.test.enabled", havingValue = "false", matchIfMissing = true)
class TossSandboxPaymentAdapter implements TossPaymentAdapter {
	@Override
	public ConfirmResult confirm(String paymentKey, String providerOrderId, BigDecimal amount) {
		if (paymentKey.startsWith("unknown_")) return new ConfirmResult("UNKNOWN", "NO_RESPONSE");
		if (paymentKey.startsWith("fail_")) return new ConfirmResult("FAILED", "ABORTED");
		return new ConfirmResult("SUCCEEDED", "DONE");
	}
	@Override public ConfirmResult queryPayment(String providerOrderId) { return providerOrderId.contains("unknown") ? new ConfirmResult("UNKNOWN","NO_RESPONSE") : new ConfirmResult("SUCCEEDED","DONE"); }
}
