package com.pawcycle.backend.commerce;

import java.math.BigDecimal;

/** Provider boundary for Toss payment confirmation. */
public interface TossPaymentAdapter {
	default boolean isConfigured() { return true; }
	ConfirmResult confirm(String paymentKey, String providerOrderId, BigDecimal amount);
	record ConfirmResult(String status, String providerStatus) {}
}
