package com.pawcycle.backend.commerce;

import java.math.BigDecimal;

/** Provider boundary for billing-key issuance and recurring charges. */
public interface TossBillingAdapter {
	default boolean isConfigured() { return true; }
	BillingKeyResult issueBillingKey(String customerKey, String authKey);
	ChargeResult charge(String billingKey, String providerOrderId, BigDecimal amount);
	record BillingKeyResult(String billingKey) { }
	record ChargeResult(String status, String providerStatus) { }
}
