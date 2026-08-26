package com.pawcycle.backend.commerce;

import java.math.BigDecimal;

/** Provider boundary for Toss payment confirmation. */
public interface TossPaymentAdapter {
	default boolean isConfigured() { return true; }
	default boolean browserTestEnabled() { return false; }
	ConfirmResult confirm(String paymentKey, String providerOrderId, BigDecimal amount);
	default ConfirmResult queryPayment(String providerOrderId) { throw new CommerceException(503,"PAYMENT_PROVIDER_UNAVAILABLE","Toss 결제 Provider가 현재 환경에 구성되지 않았습니다."); }
	record ConfirmResult(String status, String providerStatus) {}
}
