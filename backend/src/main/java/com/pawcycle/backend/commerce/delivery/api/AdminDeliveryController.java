package com.pawcycle.backend.commerce.delivery.api;

import com.pawcycle.backend.commerce.CommercePayload;
import com.pawcycle.backend.commerce.DeliveryService;
import com.pawcycle.backend.commerce.ReasonRequest;
import com.pawcycle.backend.commerce.ShipmentRequest;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/deliveries")
public class AdminDeliveryController {
  private final DeliveryService deliveries;

  public AdminDeliveryController(DeliveryService deliveries) {
    this.deliveries = deliveries;
  }

  @PostMapping("/{id}/ship")
  public CommercePayload ship(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long id,
      @Valid @RequestBody ShipmentRequest request) {
    return deliveries.ship(principal.memberId(), id, request.carrierCode(), request.trackingNumber());
  }

  @PostMapping("/{id}/complete")
  public CommercePayload complete(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal, @PathVariable long id) {
    return deliveries.complete(principal.memberId(), id);
  }

  @PostMapping("/{id}/fail")
  public CommercePayload fail(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long id,
      @Valid @RequestBody ReasonRequest request) {
    return deliveries.fail(principal.memberId(), id, request.reason());
  }
}
