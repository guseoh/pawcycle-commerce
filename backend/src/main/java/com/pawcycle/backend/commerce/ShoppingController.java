package com.pawcycle.backend.commerce;

import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api")
class ShoppingController {
	private final CommerceService commerce;
	ShoppingController(CommerceService commerce) { this.commerce = commerce; }
	@GetMapping("/cart") Map<String,Object> cart(@AuthenticationPrincipal AuthenticatedMemberPrincipal p) { return commerce.cart(p.memberId()); }
	@PostMapping("/cart/items") ResponseEntity<Void> addCart(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@Valid @RequestBody CommerceRequests.CartItem r) { commerce.addCartItem(p.memberId(),r.skuId(),r.quantity()); return ResponseEntity.noContent().build(); }
	@PatchMapping("/cart/items/{skuId}") ResponseEntity<Void> patchCart(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long skuId,@Valid @RequestBody CommerceRequests.Quantity r) { commerce.updateCartItem(p.memberId(),skuId,r.quantity()); return ResponseEntity.noContent().build(); }
	@DeleteMapping("/cart/items/{skuId}") ResponseEntity<Void> deleteCart(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long skuId) { commerce.deleteCartItem(p.memberId(),skuId); return ResponseEntity.noContent().build(); }
	@GetMapping("/wishlist") Map<String,Object> wishlist(@AuthenticationPrincipal AuthenticatedMemberPrincipal p) { return commerce.wishlist(p.memberId()); }
	@PostMapping("/wishlist/{productId}") ResponseEntity<Void> addWishlist(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long productId) { commerce.addWishlist(p.memberId(),productId); return ResponseEntity.noContent().build(); }
	@DeleteMapping("/wishlist/{productId}") ResponseEntity<Void> deleteWishlist(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long productId) { commerce.deleteWishlist(p.memberId(),productId); return ResponseEntity.noContent().build(); }
}
