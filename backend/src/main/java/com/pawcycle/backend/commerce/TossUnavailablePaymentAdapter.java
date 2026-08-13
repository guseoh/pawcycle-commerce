package com.pawcycle.backend.commerce;

import java.math.BigDecimal;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!local-integration")
class TossUnavailablePaymentAdapter implements TossPaymentAdapter {
	@Override
	public boolean isConfigured() { return false; }

	@Override
	public ConfirmResult confirm(String paymentKey, String providerOrderId, BigDecimal amount) {
		throw new CommerceException(
				503,
				"PAYMENT_PROVIDER_UNAVAILABLE",
				"Toss 결제 Provider가 현재 환경에 구성되지 않았습니다.");
	}
}
