package com.pawcycle.backend.commerce.checkout.domain;

import com.pawcycle.backend.commerce.common.error.CommerceException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Price calculation only; coupon lookup and transaction ownership remain with the use case. */
public final class CheckoutPricingPolicy {
  private CheckoutPricingPolicy() {}

  public static BigDecimal discount(BigDecimal original, BigDecimal minimum,
      String type, BigDecimal value, BigDecimal maximum) {
    if (original.compareTo(minimum) < 0) {
      throw new CommerceException(409, "COUPON_MINIMUM_ORDER", "최소 주문 금액을 충족하지 않습니다.");
    }
    BigDecimal discount = "PERCENTAGE".equals(type)
        ? original.multiply(value).divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
        : value;
    if (maximum != null) discount = discount.min(maximum);
    return discount.min(original);
  }
}
