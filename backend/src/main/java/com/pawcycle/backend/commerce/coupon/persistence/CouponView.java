package com.pawcycle.backend.commerce.coupon.persistence;

import java.math.BigDecimal;
import java.sql.Timestamp;

public record CouponView(
    long couponId,
    String name,
    String discountType,
    BigDecimal discountValue,
    BigDecimal minimumOrderAmount,
    BigDecimal maximumDiscountAmount,
    Timestamp validFrom,
    Timestamp validUntil,
    boolean active) {}
