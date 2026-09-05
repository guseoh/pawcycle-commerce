package com.pawcycle.backend.commerce.notification.api;

import com.pawcycle.backend.commerce.NotificationService;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
  private final NotificationService notifications;

  public NotificationController(NotificationService notifications) {
    this.notifications = notifications;
  }

  @GetMapping
  public List<NotificationResponse> list(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal) {
    return notifications.list(principal.memberId());
  }

  @PatchMapping("/{id}/read")
  public ResponseEntity<Void> read(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal, @PathVariable long id) {
    notifications.read(principal.memberId(), id);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/read-all")
  public ResponseEntity<Void> readAll(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal) {
    notifications.readAll(principal.memberId());
    return ResponseEntity.noContent().build();
  }
}
