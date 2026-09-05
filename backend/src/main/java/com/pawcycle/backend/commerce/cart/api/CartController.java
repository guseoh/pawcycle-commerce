package com.pawcycle.backend.commerce.cart.api;

import com.pawcycle.backend.commerce.CartItemRequest;
import com.pawcycle.backend.commerce.QuantityRequest;
import com.pawcycle.backend.commerce.cart.application.CartApplicationService;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/cart")
public class CartController {
  private final CartApplicationService cart;

  public CartController(CartApplicationService cart) {
    this.cart = cart;
  }

  @GetMapping
  public CartResponse get(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal) {
    return cart.get(principal.memberId());
  }

  @PostMapping("/items")
  public ResponseEntity<Void> add(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @Valid @RequestBody CartItemRequest request) {
    cart.add(principal.memberId(), request.skuId(), request.quantity());
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/items/{skuId}")
  public ResponseEntity<Void> update(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long skuId,
      @Valid @RequestBody QuantityRequest request) {
    cart.update(principal.memberId(), skuId, request.quantity());
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/items/{skuId}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal, @PathVariable long skuId) {
    cart.delete(principal.memberId(), skuId);
    return ResponseEntity.noContent().build();
  }
}
