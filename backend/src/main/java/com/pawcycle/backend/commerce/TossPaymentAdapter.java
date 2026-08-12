package com.pawcycle.backend.commerce;

import java.math.BigDecimal;

/** Test-only provider boundary. It never sends a network request or stores card data. */
public interface TossPaymentAdapter {
	ConfirmResult confirm(String paymentKey, String providerOrderId, BigDecimal amount);
	record ConfirmResult(String status, String providerStatus) {}
}
