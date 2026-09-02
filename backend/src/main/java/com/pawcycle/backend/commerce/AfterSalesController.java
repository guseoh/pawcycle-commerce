package com.pawcycle.backend.commerce;

import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class AfterSalesController {
  private final CancellationService cancellations;
  private final ReturnService returns;
  private final NotificationService notifications;

  AfterSalesController(
      CancellationService cancellations, ReturnService returns, NotificationService notifications) {
    this.cancellations = cancellations;
    this.returns = returns;
    this.notifications = notifications;
  }

  @PostMapping("/orders/{orderId}/cancellations")
  Map<String, Object> cancellation(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal p,
      @PathVariable long orderId,
      @Valid @RequestBody ReasonRequest request) {
    return cancellations.request(p.memberId(), orderId, request.reason());
  }

  @PostMapping("/orders/{orderId}/returns")
  Map<String, Object> returnRequest(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal p,
      @PathVariable long orderId,
      @Valid @RequestBody ReasonRequest request) {
    return returns.request(p.memberId(), orderId, request.reason());
  }

  @GetMapping("/notifications")
  List<Map<String, Object>> notifications(@AuthenticationPrincipal AuthenticatedMemberPrincipal p) {
    return notifications.list(p.memberId());
  }

  @PatchMapping("/notifications/{id}/read")
  ResponseEntity<Void> read(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal p, @PathVariable long id) {
    notifications.read(p.memberId(), id);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/notifications/read-all")
  ResponseEntity<Void> readAll(@AuthenticationPrincipal AuthenticatedMemberPrincipal p) {
    notifications.readAll(p.memberId());
    return ResponseEntity.noContent().build();
  }
}
