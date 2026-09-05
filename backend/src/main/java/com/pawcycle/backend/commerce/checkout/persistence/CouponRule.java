package com.pawcycle.backend.commerce.checkout.persistence;

import java.math.BigDecimal;

public record CouponRule(
    BigDecimal minimumOrderAmount,
    BigDecimal discountValue,
    BigDecimal maximumDiscountAmount,
    String discountType) {}
