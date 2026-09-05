package com.pawcycle.backend.commerce.membership.persistence;

import java.math.BigDecimal;

public record MembershipGradeView(
    long gradeId,
    String code,
    String name,
    BigDecimal minimumPurchaseAmount,
    int displayOrder,
    boolean active,
    Long benefitCouponId) {}
