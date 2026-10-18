package com.pawcycle.backend.commerce.membership.application;

import java.math.BigDecimal;

public record MembershipGradeCreateCommand(
    String code,
    String name,
    BigDecimal minimumPurchaseAmount,
    Integer displayOrder,
    Boolean active,
    Long benefitCouponId) {}
