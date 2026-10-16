package com.pawcycle.backend.commerce.coupon.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CouponWriteCommand(
    String name,
    String discountType,
    BigDecimal discountValue,
    BigDecimal minimumOrderAmount,
    BigDecimal maximumDiscountAmount,
    LocalDateTime validFrom,
    LocalDateTime validUntil,
    Boolean active) {}
