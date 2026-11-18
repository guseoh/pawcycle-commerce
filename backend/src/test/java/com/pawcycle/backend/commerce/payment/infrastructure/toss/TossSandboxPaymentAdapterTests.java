package com.pawcycle.backend.commerce.payment.infrastructure.toss;

import com.pawcycle.backend.commerce.payment.application.TossPaymentAdapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TossSandboxPaymentAdapterTests {
  private final TossPaymentAdapter adapter = new TossSandboxPaymentAdapter();

  @Test
  void mapsDeterministicSandboxOutcomesWithoutProviderNetworkAccess() {
    assertThat(adapter.confirm("payment-key", "order-1", BigDecimal.valueOf(100)).status())
        .isEqualTo("SUCCEEDED");
    assertThat(adapter.confirm("fail_test", "order-1", BigDecimal.valueOf(100)).status())
        .isEqualTo("FAILED");
    assertThat(adapter.confirm("unknown_test", "order-1", BigDecimal.valueOf(100)).status())
        .isEqualTo("UNKNOWN");
  }

  @Test
  void queryMapsDeterministicOutcomesForLocalRecoveryCommands() {
    BigDecimal amount = BigDecimal.valueOf(100);
    assertThat(adapter.queryPayment("order-1", "key", amount))
        .isEqualTo(new TossPaymentAdapter.ConfirmResult("SUCCEEDED", "DONE"));
    assertThat(adapter.queryPayment("order-unknown-1", "key", amount))
        .isEqualTo(new TossPaymentAdapter.ConfirmResult("UNKNOWN", "NO_RESPONSE"));
    assertThat(adapter.queryPayment("order-aborted-1", "key", amount))
        .isEqualTo(new TossPaymentAdapter.ConfirmResult("FAILED", "ABORTED"));
    assertThat(adapter.queryPayment("order-expired-1", "key", amount))
        .isEqualTo(new TossPaymentAdapter.ConfirmResult("FAILED", "EXPIRED"));
  }
}
