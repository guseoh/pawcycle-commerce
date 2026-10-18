package com.pawcycle.backend.commerce.coupon.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

public record MemberCouponView(
    long memberCouponId,
    long couponId,
    String name,
    String discountType,
    BigDecimal discountValue,
    String status,
    Timestamp validFrom,
    Timestamp validUntil) {}
