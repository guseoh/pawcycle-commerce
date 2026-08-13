package com.pawcycle.backend.commerce;

import java.math.BigDecimal;

/** Provider boundary for billing-key issuance and recurring charges. */
public interface TossBillingAdapter {
	default boolean isConfigured() { return true; }
	BillingKeyResult issueBillingKey(String customerKey, String authKey);
	ChargeResult charge(String billingKey, String providerOrderId, BigDecimal amount);
	default ChargeResult queryCharge(String providerOrderId) { throw new CommerceException(503,"PAYMENT_PROVIDER_UNAVAILABLE","Toss Billing Provider가 현재 환경에 구성되지 않았습니다."); }
	record BillingKeyResult(String billingKey) { }
	record ChargeResult(String status, String providerStatus) { }
}
