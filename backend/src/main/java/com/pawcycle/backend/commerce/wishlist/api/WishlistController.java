package com.pawcycle.backend.commerce.wishlist.api;

import com.pawcycle.backend.commerce.wishlist.application.WishlistApplicationService;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {
  private final WishlistApplicationService wishlist;

  public WishlistController(WishlistApplicationService wishlist) {
    this.wishlist = wishlist;
  }

  @GetMapping
  public WishlistResponse list(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal) {
    return wishlist.list(principal.memberId());
  }

  @PostMapping("/{productId}")
  public ResponseEntity<Void> add(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal, @PathVariable long productId) {
    wishlist.add(principal.memberId(), productId);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{productId}")
  public ResponseEntity<Void> remove(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal, @PathVariable long productId) {
    wishlist.remove(principal.memberId(), productId);
    return ResponseEntity.noContent().build();
  }
}
