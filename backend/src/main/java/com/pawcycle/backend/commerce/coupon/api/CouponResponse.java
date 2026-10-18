package com.pawcycle.backend.commerce.coupon.api;

import com.pawcycle.backend.commerce.coupon.model.CouponView;
import java.math.BigDecimal;
import java.sql.Timestamp;

public record CouponResponse(
    long couponId,
    String name,
    String discountType,
    BigDecimal discountValue,
    BigDecimal minimumOrderAmount,
    BigDecimal maximumDiscountAmount,
    Timestamp validFrom,
    Timestamp validUntil,
    boolean active) {
  public static CouponResponse from(CouponView view) {
    return new CouponResponse(
        view.couponId(),
        view.name(),
        view.discountType(),
        view.discountValue(),
        view.minimumOrderAmount(),
        view.maximumDiscountAmount(),
        view.validFrom(),
        view.validUntil(),
        view.active());
  }
}
