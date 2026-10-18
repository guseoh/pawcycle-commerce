package com.pawcycle.backend.commerce.membership.application;

import com.pawcycle.backend.commerce.coupon.persistence.CouponPersistenceAdapter;
import com.pawcycle.backend.commerce.coupon.model.MemberCouponView;
import com.pawcycle.backend.commerce.membership.persistence.MembershipQueryRepository;
import com.pawcycle.backend.commerce.membership.model.MembershipView;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberBenefitApplicationService {
  private final CouponPersistenceAdapter coupons;
  private final MembershipQueryRepository membership;

  public MemberBenefitApplicationService(
      CouponPersistenceAdapter coupons, MembershipQueryRepository membership) {
    this.coupons = coupons;
    this.membership = membership;
  }

  @Transactional(readOnly = true)
  public List<MemberCouponView> coupons(long memberId) {
    return List.copyOf(coupons.findForMember(memberId));
  }

  @Transactional(readOnly = true)
  public MembershipView membership(long memberId) {
    return membership.findForMember(memberId);
  }
}
