package com.pawcycle.backend.commerce.membership.api;

import com.pawcycle.backend.commerce.membership.model.MembershipGradeView;
import java.math.BigDecimal;

public record MembershipGradeResponse(
    long gradeId,
    String code,
    String name,
    BigDecimal minimumPurchaseAmount,
    int displayOrder,
    boolean active,
    Long benefitCouponId) {
  public static MembershipGradeResponse from(MembershipGradeView view) {
    return new MembershipGradeResponse(
        view.gradeId(),
        view.code(),
        view.name(),
        view.minimumPurchaseAmount(),
        view.displayOrder(),
        view.active(),
        view.benefitCouponId());
  }
}
