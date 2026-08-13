package com.pawcycle.backend.commerce;

import java.math.BigDecimal;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local-integration")
class TossSandboxPaymentAdapter implements TossPaymentAdapter {
	@Override
	public ConfirmResult confirm(String paymentKey, String providerOrderId, BigDecimal amount) {
		if (paymentKey.startsWith("unknown_")) return new ConfirmResult("UNKNOWN", "NO_RESPONSE");
		if (paymentKey.startsWith("fail_")) return new ConfirmResult("FAILED", "ABORTED");
		return new ConfirmResult("SUCCEEDED", "DONE");
	}
}
