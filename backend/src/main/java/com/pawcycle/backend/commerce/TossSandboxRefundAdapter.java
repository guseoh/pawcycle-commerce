package com.pawcycle.backend.commerce;

import java.math.BigDecimal;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local-integration")
class TossSandboxRefundAdapter implements TossRefundAdapter {
	public RefundResult refund(String idempotencyKey, BigDecimal amount) { return idempotencyKey.contains("unknown") ? new RefundResult("UNKNOWN","NO_RESPONSE") : new RefundResult("SUCCEEDED","DONE"); }
	public RefundResult reconcile(String idempotencyKey) { return refund(idempotencyKey,BigDecimal.ZERO); }
}
