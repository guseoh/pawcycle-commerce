package com.pawcycle.backend.commerce.returnrequest.api;

import com.pawcycle.backend.commerce.CommercePayload;
import com.pawcycle.backend.commerce.ReasonRequest;
import com.pawcycle.backend.commerce.ReturnReceiptRequest;
import com.pawcycle.backend.commerce.ReturnService;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/returns")
public class AdminReturnController {
  private final ReturnService returns;

  public AdminReturnController(ReturnService returns) {
    this.returns = returns;
  }

  @PostMapping("/{id}/approve")
  public CommercePayload approve(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal, @PathVariable long id) {
    return returns.approve(principal.memberId(), id);
  }

  @PostMapping("/{id}/reject")
  public CommercePayload reject(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long id,
      @Valid @RequestBody ReasonRequest request) {
    return returns.reject(principal.memberId(), id, request.reason());
  }

  @PostMapping("/{id}/receive")
  public CommercePayload receive(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long id,
      @Valid @RequestBody ReturnReceiptRequest request) {
    return returns.receive(principal.memberId(), id, request.restock());
  }
}
