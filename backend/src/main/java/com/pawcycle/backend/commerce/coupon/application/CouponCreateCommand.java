package com.pawcycle.backend.commerce.coupon.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CouponCreateCommand(
    String name,
    String discountType,
    BigDecimal discountValue,
    BigDecimal minimumOrderAmount,
    BigDecimal maximumDiscountAmount,
    LocalDateTime validFrom,
    LocalDateTime validUntil,
    Boolean active) {}
