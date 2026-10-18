package com.pawcycle.backend.commerce.coupon.api;

import com.pawcycle.backend.commerce.coupon.model.MemberCouponView;
import java.math.BigDecimal;
import java.sql.Timestamp;

public record MemberCouponResponse(
    long memberCouponId,
    long couponId,
    String name,
    String discountType,
    BigDecimal discountValue,
    String status,
    Timestamp validFrom,
    Timestamp validUntil) {
  public static MemberCouponResponse from(MemberCouponView view) {
    return new MemberCouponResponse(
        view.memberCouponId(),
        view.couponId(),
        view.name(),
        view.discountType(),
        view.discountValue(),
        view.status(),
        view.validFrom(),
        view.validUntil());
  }
}
