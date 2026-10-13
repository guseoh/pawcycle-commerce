package com.pawcycle.backend.commerce.membership.application;

import java.math.BigDecimal;

public record CreateMembershipGradeCommand(
    String code, String name, BigDecimal minimumPurchaseAmount,
    int displayOrder, boolean active, Long benefitCouponId) {}
