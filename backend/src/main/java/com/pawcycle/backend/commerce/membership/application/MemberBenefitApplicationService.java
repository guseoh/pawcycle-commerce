package com.pawcycle.backend.commerce.membership.application;

import com.pawcycle.backend.commerce.coupon.api.MemberCouponResponse;
import com.pawcycle.backend.commerce.coupon.persistence.CouponPersistenceAdapter;
import com.pawcycle.backend.commerce.coupon.persistence.MemberCouponView;
import com.pawcycle.backend.commerce.membership.api.MembershipResponse;
import com.pawcycle.backend.commerce.membership.persistence.MembershipPersistenceAdapter;
import com.pawcycle.backend.commerce.membership.persistence.MembershipView;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberBenefitApplicationService {
  private final CouponPersistenceAdapter coupons;
  private final MembershipPersistenceAdapter membership;

  public MemberBenefitApplicationService(
      CouponPersistenceAdapter coupons, MembershipPersistenceAdapter membership) {
    this.coupons = coupons;
    this.membership = membership;
  }

  @Transactional(readOnly = true)
  public List<MemberCouponResponse> coupons(long memberId) {
    return coupons.findForMember(memberId).stream()
        .map(MemberBenefitApplicationService::coupon)
        .toList();
  }

  @Transactional(readOnly = true)
  public MembershipResponse membership(long memberId) {
    MembershipView view = membership.findForMember(memberId);
    return new MembershipResponse(
        view.code(), view.name(), view.evaluatedPurchaseAmount(), view.evaluatedAt());
  }

  private static MemberCouponResponse coupon(MemberCouponView view) {
    return new MemberCouponResponse(
        view.memberCouponId(),
        view.couponId(),
        view.name(),
        view.discountType(),
        view.discountValue(),
        view.status(),
        view.validFrom(),
        view.validUntil());
  }
}
