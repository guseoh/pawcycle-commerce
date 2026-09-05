package com.pawcycle.backend.commerce.membership.api;

import com.pawcycle.backend.commerce.MembershipGradeCreatedResponse;
import com.pawcycle.backend.commerce.MembershipGradeRequest;
import com.pawcycle.backend.commerce.membership.application.MembershipAdminApplicationService;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminMembershipController {
  private final MembershipAdminApplicationService membership;

  public AdminMembershipController(MembershipAdminApplicationService membership) {
    this.membership = membership;
  }

  @GetMapping("/membership-grades")
  public List<MembershipGradeResponse> grades() {
    return membership.listGrades();
  }

  @PostMapping("/membership-grades")
  public ResponseEntity<MembershipGradeCreatedResponse> createGrade(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @Valid @RequestBody MembershipGradeRequest request) {
    long id = membership.createGrade(principal.memberId(), request);
    return ResponseEntity.created(URI.create("/api/admin/membership-grades/" + id))
        .body(new MembershipGradeCreatedResponse(id));
  }

  @PostMapping("/members/{memberId}/membership/evaluate")
  public ResponseEntity<Void> evaluate(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal, @PathVariable long memberId) {
    membership.evaluate(principal.memberId(), memberId);
    return ResponseEntity.noContent().build();
  }
}
