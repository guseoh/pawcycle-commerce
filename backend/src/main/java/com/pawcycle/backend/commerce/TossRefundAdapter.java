package com.pawcycle.backend.commerce;

import java.math.BigDecimal;

/** Provider boundary for refunds. It deliberately exposes no payment or billing secrets. */
public interface TossRefundAdapter {
	default boolean isConfigured() { return true; }
	RefundResult refund(String idempotencyKey, BigDecimal amount);
	RefundResult reconcile(String idempotencyKey);
	record RefundResult(String status, String providerStatus) { }
}
