package com.pawcycle.backend.commerce.coupon.api;

import com.pawcycle.backend.commerce.membership.application.MemberBenefitApplicationService;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coupons")
public class MemberCouponController {
  private final MemberBenefitApplicationService memberBenefits;

  public MemberCouponController(MemberBenefitApplicationService memberBenefits) {
    this.memberBenefits = memberBenefits;
  }

  @GetMapping
  public List<MemberCouponResponse> list(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal) {
    return memberBenefits.coupons(principal.memberId());
  }
}
