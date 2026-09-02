package com.pawcycle.backend.commerce;

import java.math.BigDecimal;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!local-integration")
class TossUnavailableRefundAdapter implements TossRefundAdapter {
  public boolean isConfigured() {
    return false;
  }

  public RefundResult refund(String key, BigDecimal amount) {
    throw new CommerceException(
        503, "REFUND_PROVIDER_UNAVAILABLE", "Toss 환불 Provider가 현재 환경에 구성되지 않았습니다.");
  }

  public RefundResult reconcile(String key) {
    return refund(key, BigDecimal.ZERO);
  }
}
