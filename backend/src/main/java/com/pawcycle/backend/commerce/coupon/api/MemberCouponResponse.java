package com.pawcycle.backend.commerce.coupon.api;

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
    Timestamp validUntil) {}
