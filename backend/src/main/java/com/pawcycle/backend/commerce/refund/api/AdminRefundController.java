package com.pawcycle.backend.commerce.refund.api;

import com.pawcycle.backend.commerce.CommercePayload;
import com.pawcycle.backend.commerce.RefundService;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/refunds")
public class AdminRefundController {
  private final RefundService refunds;

  public AdminRefundController(RefundService refunds) {
    this.refunds = refunds;
  }

  @PostMapping("/{id}/process")
  public CommercePayload process(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal, @PathVariable long id) {
    return refunds.process(id, principal.memberId());
  }

  @PostMapping("/{id}/retry")
  public CommercePayload retry(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal, @PathVariable long id) {
    return refunds.retry(id, principal.memberId());
  }

  @PostMapping("/{id}/reconcile")
  public CommercePayload reconcile(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal, @PathVariable long id) {
    return refunds.reconcile(id, principal.memberId());
  }
}
