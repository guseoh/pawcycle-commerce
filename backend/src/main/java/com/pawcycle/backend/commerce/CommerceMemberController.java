package com.pawcycle.backend.commerce;

import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Address, shipping and member benefit HTTP adapter. */
@RestController @RequestMapping("/api")
class CommerceMemberController {
	private final CommerceService commerce;
	CommerceMemberController(CommerceService commerce) { this.commerce = commerce; }
	@GetMapping("/addresses") List<Map<String,Object>> addresses(@AuthenticationPrincipal AuthenticatedMemberPrincipal p) { return commerce.addresses(p.memberId()); }
	@PostMapping("/addresses") ResponseEntity<Map<String,Object>> createAddress(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@Valid @RequestBody CommerceRequests.Address r) { long id=commerce.createAddress(p.memberId(),r.legacyPayload()); return ResponseEntity.created(URI.create("/api/addresses/"+id)).body(Map.of("addressId",id)); }
	@PatchMapping("/addresses/{addressId}") ResponseEntity<Void> patchAddress(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long addressId,@Valid @RequestBody CommerceRequests.Address r) { commerce.updateAddress(p.memberId(),addressId,r.legacyPayload()); return ResponseEntity.noContent().build(); }
	@DeleteMapping("/addresses/{addressId}") ResponseEntity<Void> deleteAddress(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long addressId) { commerce.deleteAddress(p.memberId(),addressId); return ResponseEntity.noContent().build(); }
	@PutMapping("/addresses/{addressId}/default") ResponseEntity<Void> defaultAddress(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long addressId) { commerce.defaultAddress(p.memberId(),addressId); return ResponseEntity.noContent().build(); }
	@PutMapping("/subscriptions/{subscriptionId}/shipping-address") ResponseEntity<Void> updateShipping(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long subscriptionId,@Valid @RequestBody CommerceRequests.Address request) { commerce.updateSubscriptionShipping(p.memberId(),subscriptionId,request.legacyPayload()); return ResponseEntity.noContent().build(); }
	@GetMapping("/coupons") List<Map<String,Object>> memberCoupons(@AuthenticationPrincipal AuthenticatedMemberPrincipal p) { return commerce.memberCoupons(p.memberId()); }
	@GetMapping("/membership") Map<String,Object> membership(@AuthenticationPrincipal AuthenticatedMemberPrincipal p) { return commerce.membership(p.memberId()); }
}
