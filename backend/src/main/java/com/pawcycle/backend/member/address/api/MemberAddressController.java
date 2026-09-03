package com.pawcycle.backend.member.address.api;

import com.pawcycle.backend.commerce.AddressCreatedResponse;
import com.pawcycle.backend.commerce.AddressRequest;
import com.pawcycle.backend.commerce.CommerceRowResponse;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.member.address.application.MemberAddressApplicationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MemberAddressController {
  private final MemberAddressApplicationService addresses;

  public MemberAddressController(MemberAddressApplicationService addresses) {
    this.addresses = addresses;
  }

  @GetMapping("/addresses")
  public List<CommerceRowResponse> list(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal) {
    return addresses.list(principal.memberId());
  }

  @PostMapping("/addresses")
  public ResponseEntity<AddressCreatedResponse> create(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @Valid @RequestBody AddressRequest request) {
    long id = addresses.create(principal.memberId(), request);
    return ResponseEntity.created(URI.create("/api/addresses/" + id))
        .body(new AddressCreatedResponse(id));
  }

  @PatchMapping("/addresses/{addressId}")
  public ResponseEntity<Void> update(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long addressId,
      @Valid @RequestBody AddressRequest request) {
    addresses.update(principal.memberId(), addressId, request);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/addresses/{addressId}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long addressId) {
    addresses.delete(principal.memberId(), addressId);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/addresses/{addressId}/default")
  public ResponseEntity<Void> makeDefault(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long addressId) {
    addresses.makeDefault(principal.memberId(), addressId);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/subscriptions/{subscriptionId}/shipping-address")
  public ResponseEntity<Void> updateSubscriptionShipping(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long subscriptionId,
      @Valid @RequestBody AddressRequest request) {
    addresses.updateSubscriptionShipping(principal.memberId(), subscriptionId, request);
    return ResponseEntity.noContent().build();
  }
}
