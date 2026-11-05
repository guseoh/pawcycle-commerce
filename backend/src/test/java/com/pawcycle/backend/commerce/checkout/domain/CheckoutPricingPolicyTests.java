package com.pawcycle.backend.commerce.checkout.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CheckoutPricingPolicyTests {
  @Test void percentageRoundsDownAndHonorsBothCaps() {
    assertThat(discount("999", "0", "PERCENTAGE", "10", null)).isEqualByComparingTo("99");
    assertThat(discount("999", "0", "PERCENTAGE", "10", "50")).isEqualByComparingTo("50");
    assertThat(discount("999", "0", "FIXED", "1000", null)).isEqualByComparingTo("999");
    assertThat(discount("999", "999", "FIXED", "100", null)).isEqualByComparingTo("100");
  }
  @Test void rejectsBelowMinimum() {
    assertThatThrownBy(() -> discount("998", "999", "FIXED", "100", null))
        .hasMessage("최소 주문 금액을 충족하지 않습니다.");
  }
  private BigDecimal discount(String amount, String minimum, String type, String value, String maximum) {
    return CheckoutPricingPolicy.discount(new BigDecimal(amount), new BigDecimal(minimum), type,
        new BigDecimal(value), maximum == null ? null : new BigDecimal(maximum));
  }
}
