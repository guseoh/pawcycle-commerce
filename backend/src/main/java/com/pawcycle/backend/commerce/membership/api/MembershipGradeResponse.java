package com.pawcycle.backend.commerce.membership.api;

import java.math.BigDecimal;

public record MembershipGradeResponse(
    long gradeId,
    String code,
    String name,
    BigDecimal minimumPurchaseAmount,
    int displayOrder,
    boolean active,
    Long benefitCouponId) {}
