package com.pawcycle.backend.commerce.coupon.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 쿠폰 부분 수정 유스케이스의 presence-aware 입력이다. */
public record CouponPatchCommand(
    String name,
    String discountType,
    BigDecimal discountValue,
    BigDecimal minimumOrderAmount,
    BigDecimal maximumDiscountAmount,
    LocalDateTime validFrom,
    LocalDateTime validUntil,
    Boolean active,
    boolean namePresent,
    boolean discountTypePresent,
    boolean discountValuePresent,
    boolean minimumOrderAmountPresent,
    boolean maximumDiscountAmountPresent,
    boolean validFromPresent,
    boolean validUntilPresent,
    boolean activePresent) {
  public boolean hasName() {
    return namePresent;
  }

  public boolean hasDiscountType() {
    return discountTypePresent;
  }

  public boolean hasDiscountValue() {
    return discountValuePresent;
  }

  public boolean hasMinimumOrderAmount() {
    return minimumOrderAmountPresent;
  }

  public boolean hasMaximumDiscountAmount() {
    return maximumDiscountAmountPresent;
  }

  public boolean hasValidFrom() {
    return validFromPresent;
  }

  public boolean hasValidUntil() {
    return validUntilPresent;
  }

  public boolean hasActive() {
    return activePresent;
  }

  public boolean isEmpty() {
    return !namePresent
        && !discountTypePresent
        && !discountValuePresent
        && !minimumOrderAmountPresent
        && !maximumDiscountAmountPresent
        && !validFromPresent
        && !validUntilPresent
        && !activePresent;
  }
}
