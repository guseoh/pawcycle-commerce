package com.pawcycle.backend.commerce;

import com.pawcycle.backend.commerce.coupon.api.CouponResponse;
import com.pawcycle.backend.commerce.coupon.api.MemberCouponResponse;
import com.pawcycle.backend.commerce.coupon.model.CouponView;
import com.pawcycle.backend.commerce.coupon.model.MemberCouponView;
import com.pawcycle.backend.commerce.membership.api.MembershipResponse;
import com.pawcycle.backend.commerce.membership.api.MembershipGradeResponse;
import com.pawcycle.backend.commerce.membership.model.MembershipView;
import com.pawcycle.backend.commerce.membership.model.MembershipGradeView;

// Frozen constructor mappings from main 9d12061bf2188442fa1c0c3637fbfebff2ed11cd.
public final class FrozenMemberBenefitResponses {
  private FrozenMemberBenefitResponses() {}

  public static CouponResponse coupon(CouponView view) {
    return new CouponResponse(
        view.couponId(), view.name(), view.discountType(), view.discountValue(),
        view.minimumOrderAmount(), view.maximumDiscountAmount(), view.validFrom(),
        view.validUntil(), view.active());
  }

  public static MemberCouponResponse memberCoupon(MemberCouponView view) {
    return new MemberCouponResponse(
        view.memberCouponId(), view.couponId(), view.name(), view.discountType(),
        view.discountValue(), view.status(), view.validFrom(), view.validUntil());
  }

  public static MembershipResponse membership(MembershipView view) {
    return new MembershipResponse(
        view.code(), view.name(), view.evaluatedPurchaseAmount(), view.evaluatedAt());
  }

  public static MembershipGradeResponse grade(MembershipGradeView view) {
    return new MembershipGradeResponse(
        view.gradeId(), view.code(), view.name(), view.minimumPurchaseAmount(),
        view.displayOrder(), view.active(), view.benefitCouponId());
  }
}
