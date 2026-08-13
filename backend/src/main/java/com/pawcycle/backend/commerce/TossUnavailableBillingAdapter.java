package com.pawcycle.backend.commerce;

import java.math.BigDecimal;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!local-integration")
class TossUnavailableBillingAdapter implements TossBillingAdapter {
	@Override public boolean isConfigured() { return false; }
	@Override public BillingKeyResult issueBillingKey(String customerKey, String authKey) { throw unavailable(); }
	@Override public ChargeResult charge(String billingKey, String providerOrderId, BigDecimal amount) { throw unavailable(); }
	private CommerceException unavailable() { return new CommerceException(503,"PAYMENT_PROVIDER_UNAVAILABLE","Toss Billing Provider가 현재 환경에 구성되지 않았습니다."); }
}
