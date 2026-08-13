package com.pawcycle.backend.commerce;

import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CommerceController {
	private final CommerceService commerce;
	private final AdminAuditService audits;
	public CommerceController(CommerceService commerce, AdminAuditService audits) { this.commerce = commerce; this.audits=audits; }

	@GetMapping("/cart") Map<String,Object> cart(@AuthenticationPrincipal AuthenticatedMemberPrincipal p) { return commerce.cart(p.memberId()); }
	@PostMapping("/cart/items") ResponseEntity<Void> addCart(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@Valid @RequestBody CartItemRequest r) { commerce.addCartItem(p.memberId(),r.skuId(),r.quantity()); return ResponseEntity.noContent().build(); }
	@PatchMapping("/cart/items/{skuId}") ResponseEntity<Void> patchCart(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long skuId,@Valid @RequestBody QuantityRequest r) { commerce.updateCartItem(p.memberId(),skuId,r.quantity()); return ResponseEntity.noContent().build(); }
	@DeleteMapping("/cart/items/{skuId}") ResponseEntity<Void> deleteCart(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long skuId) { commerce.deleteCartItem(p.memberId(),skuId); return ResponseEntity.noContent().build(); }

	@GetMapping("/wishlist") Map<String,Object> wishlist(@AuthenticationPrincipal AuthenticatedMemberPrincipal p) { return commerce.wishlist(p.memberId()); }
	@PostMapping("/wishlist/{productId}") ResponseEntity<Void> addWishlist(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long productId) { commerce.addWishlist(p.memberId(),productId); return ResponseEntity.noContent().build(); }
	@DeleteMapping("/wishlist/{productId}") ResponseEntity<Void> deleteWishlist(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long productId) { commerce.deleteWishlist(p.memberId(),productId); return ResponseEntity.noContent().build(); }

	@GetMapping("/addresses") List<Map<String,Object>> addresses(@AuthenticationPrincipal AuthenticatedMemberPrincipal p) { return commerce.addresses(p.memberId()); }
	@PostMapping("/addresses") ResponseEntity<Map<String,Object>> createAddress(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@RequestBody Map<String,Object> r) { long id=commerce.createAddress(p.memberId(),r); return ResponseEntity.created(URI.create("/api/addresses/"+id)).body(Map.of("addressId",id)); }
	@PatchMapping("/addresses/{addressId}") ResponseEntity<Void> patchAddress(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long addressId,@RequestBody Map<String,Object> r) { commerce.updateAddress(p.memberId(),addressId,r); return ResponseEntity.noContent().build(); }
	@DeleteMapping("/addresses/{addressId}") ResponseEntity<Void> deleteAddress(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long addressId) { commerce.deleteAddress(p.memberId(),addressId); return ResponseEntity.noContent().build(); }
	@PutMapping("/addresses/{addressId}/default") ResponseEntity<Void> defaultAddress(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long addressId) { commerce.defaultAddress(p.memberId(),addressId); return ResponseEntity.noContent().build(); }
	@PutMapping("/subscriptions/{subscriptionId}/shipping-address") ResponseEntity<Void> updateSubscriptionShipping(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long subscriptionId,@RequestBody Map<String,Object> request) { commerce.updateSubscriptionShipping(p.memberId(),subscriptionId,request); return ResponseEntity.noContent().build(); }

	@PostMapping("/checkout") Map<String,Object> checkout(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody CheckoutRequest r) { return commerce.checkout(p.memberId(),key,r.addressId(),r.memberCouponId()); }
	@PostMapping("/payments/toss/confirm") Map<String,Object> confirm(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@Valid @RequestBody ConfirmRequest r) { return commerce.confirm(p.memberId(),r.paymentKey(),r.providerOrderId(),r.amount()); }
	@GetMapping("/orders") List<Map<String,Object>> orders(@AuthenticationPrincipal AuthenticatedMemberPrincipal p) { return commerce.orders(p.memberId()); }
	@GetMapping("/orders/{orderId}") Map<String,Object> order(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long orderId) { return commerce.order(p.memberId(),orderId); }

	@PostMapping("/payment-methods/toss/billing/prepare") Map<String,Object> prepareBilling(@AuthenticationPrincipal AuthenticatedMemberPrincipal p) { return commerce.prepareBilling(p.memberId()); }
	@PostMapping("/payment-methods/toss/billing/complete") ResponseEntity<Void> completeBilling(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@Valid @RequestBody BillingCompleteRequest r) { commerce.completeBilling(p.memberId(),r.prepareToken(),r.authKey()); return ResponseEntity.noContent().build(); }
	@GetMapping("/coupons") List<Map<String,Object>> memberCoupons(@AuthenticationPrincipal AuthenticatedMemberPrincipal p) { return commerce.memberCoupons(p.memberId()); }
	@GetMapping("/membership") Map<String,Object> membership(@AuthenticationPrincipal AuthenticatedMemberPrincipal p) { return commerce.membership(p.memberId()); }

	@GetMapping("/admin/inventories") List<Map<String,Object>> inventories() { return commerce.inventories(); }
	@PostMapping("/admin/inventories/{skuId}/adjustments") @Transactional ResponseEntity<Void> adjustInventory(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long skuId,@Valid @RequestBody AdjustmentRequest r) { commerce.adjustInventory(skuId,r.delta()); audits.append(p.memberId(),"INVENTORY_ADJUST","SKU",skuId); return ResponseEntity.noContent().build(); }
	@GetMapping("/admin/coupons") List<Map<String,Object>> coupons() { return commerce.coupons(); }
	@PostMapping("/admin/coupons") @Transactional ResponseEntity<Map<String,Object>> createCoupon(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@RequestBody Map<String,Object> request) { long id=commerce.createCoupon(request); audits.append(p.memberId(),"COUPON_CREATE","COUPON",id); return ResponseEntity.created(URI.create("/api/admin/coupons/"+id)).body(Map.of("couponId",id)); }
	@PatchMapping("/admin/coupons/{couponId}") @Transactional ResponseEntity<Void> patchCoupon(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long couponId,@RequestBody Map<String,Object> request) { commerce.updateCoupon(couponId,request); audits.append(p.memberId(),"COUPON_UPDATE","COUPON",couponId); return ResponseEntity.noContent().build(); }
	@PostMapping("/admin/coupons/{couponId}/issues") @Transactional ResponseEntity<Void> issueCoupon(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long couponId,@Valid @RequestBody CouponIssueRequest request) { commerce.issueCoupon(couponId,request.memberId()); audits.append(p.memberId(),"COUPON_ISSUE","COUPON",couponId); return ResponseEntity.noContent().build(); }
	@GetMapping("/admin/membership-grades") List<Map<String,Object>> membershipGrades() { return commerce.membershipGrades(); }
	@PostMapping("/admin/membership-grades") @Transactional ResponseEntity<Map<String,Object>> createMembershipGrade(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@RequestBody Map<String,Object> request) { long id=commerce.createMembershipGrade(request); audits.append(p.memberId(),"MEMBERSHIP_GRADE_CREATE","MEMBERSHIP_GRADE",id); return ResponseEntity.created(URI.create("/api/admin/membership-grades/"+id)).body(Map.of("gradeId",id)); }
	@PostMapping("/admin/members/{memberId}/membership/evaluate") @Transactional ResponseEntity<Void> evaluateMembership(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long memberId) { commerce.evaluateMembership(memberId); audits.append(p.memberId(),"MEMBERSHIP_EVALUATE","MEMBER",memberId); return ResponseEntity.noContent().build(); }

	public record CartItemRequest(@NotNull @Positive Long skuId,@NotNull @Positive Integer quantity) {}
	public record QuantityRequest(@NotNull @Positive Integer quantity) {}
	public record CheckoutRequest(@NotNull @Positive Long addressId,@Positive Long memberCouponId) {}
	public record ConfirmRequest(@NotBlank String paymentKey,@NotBlank String providerOrderId,@NotNull @DecimalMin("0.00") BigDecimal amount) {}
	public record BillingCompleteRequest(@NotBlank String prepareToken,@NotBlank String authKey) {}
	public record AdjustmentRequest(@NotNull Integer delta) {}
	public record CouponIssueRequest(@NotNull @Positive Long memberId) {}
}
