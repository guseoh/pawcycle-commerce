package com.pawcycle.backend.commerce.membership.api;

import com.pawcycle.backend.commerce.membership.application.MemberBenefitApplicationService;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/membership")
public class MembershipController {
  private final MemberBenefitApplicationService memberBenefits;

  public MembershipController(MemberBenefitApplicationService memberBenefits) {
    this.memberBenefits = memberBenefits;
  }

  @GetMapping
  public MembershipResponse get(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal) {
    return memberBenefits.membership(principal.memberId());
  }
}
