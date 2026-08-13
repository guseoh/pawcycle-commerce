package com.pawcycle.backend.commerce;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local-integration")
class TossSandboxBillingAdapter implements TossBillingAdapter {
	@Override public BillingKeyResult issueBillingKey(String customerKey, String authKey) {
		if (authKey.startsWith("fail_")) throw new CommerceException(503,"PAYMENT_PROVIDER_UNAVAILABLE","Toss Billing Provider가 현재 요청을 처리할 수 없습니다.");
		return new BillingKeyResult("sandbox-billing-" + UUID.randomUUID());
	}
	@Override public ChargeResult charge(String billingKey, String providerOrderId, BigDecimal amount) {
		if (providerOrderId.contains("unknown")) return new ChargeResult("UNKNOWN","NO_RESPONSE");
		if (providerOrderId.contains("fail")) return new ChargeResult("FAILED","ABORTED");
		return new ChargeResult("SUCCEEDED","DONE");
	}
}
