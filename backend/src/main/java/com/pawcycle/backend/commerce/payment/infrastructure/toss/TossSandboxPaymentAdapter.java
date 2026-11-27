package com.pawcycle.backend.commerce.payment.infrastructure.toss;

import com.pawcycle.backend.commerce.payment.application.TossPaymentAdapter;

import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local-integration")
@ConditionalOnProperty(
    name = "pawcycle.toss.test.enabled",
    havingValue = "false",
    matchIfMissing = true)
class TossSandboxPaymentAdapter implements TossPaymentAdapter {
  @Override
  public ConfirmResult confirm(String paymentKey, String providerOrderId, BigDecimal amount) {
    if (paymentKey.startsWith("unknown_")) return new ConfirmResult("UNKNOWN", "NO_RESPONSE");
    if (paymentKey.startsWith("fail_")) return new ConfirmResult("FAILED", "ABORTED");
    return new ConfirmResult("SUCCEEDED", "DONE");
  }

  /**
   * Local stand-in for the provider query. "aborted"/"expired" in the provider order id let the
   * #417 failure confirmation command be exercised end to end on local-integration.
   */
  @Override
  public ConfirmResult queryPayment(String providerOrderId, String paymentKey, BigDecimal amount) {
    if (providerOrderId.contains("unknown")) return new ConfirmResult("UNKNOWN", "NO_RESPONSE");
    if (providerOrderId.contains("aborted")) return new ConfirmResult("FAILED", "ABORTED");
    if (providerOrderId.contains("expired")) return new ConfirmResult("FAILED", "EXPIRED");
    return new ConfirmResult("SUCCEEDED", "DONE");
  }
}
