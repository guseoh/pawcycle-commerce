package com.pawcycle.backend.commerce.coupon.api;

import com.pawcycle.backend.commerce.CouponCreatedResponse;
import com.pawcycle.backend.commerce.CouponIssueRequest;
import com.pawcycle.backend.commerce.CouponRequest;
import com.pawcycle.backend.commerce.coupon.application.CouponAdminApplicationService;
import com.pawcycle.backend.commerce.coupon.application.CouponPatchCommand;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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
@RequestMapping("/api/admin/coupons")
public class AdminCouponController {
  private final CouponAdminApplicationService coupons;

  public AdminCouponController(CouponAdminApplicationService coupons) {
    this.coupons = coupons;
  }

  @GetMapping
  public List<CouponResponse> coupons() {
    return coupons.list().stream().map(AdminCouponController::response).toList();
  }

  @PostMapping
  public ResponseEntity<CouponCreatedResponse> create(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @Valid @RequestBody CouponRequest request) {
    long id = coupons.create(principal.memberId(), request);
    return ResponseEntity.created(URI.create("/api/admin/coupons/" + id))
        .body(new CouponCreatedResponse(id));
  }

  @PatchMapping("/{couponId}")
  public ResponseEntity<Void> update(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long couponId,
      @RequestBody CouponPatchRequest request) {
    coupons.update(principal.memberId(), couponId, command(request));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{couponId}/issues")
  public ResponseEntity<Void> issue(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long couponId,
      @Valid @RequestBody CouponIssueRequest request) {
    coupons.issue(principal.memberId(), couponId, request.memberId());
    return ResponseEntity.noContent().build();
  }

  private static CouponPatchCommand command(CouponPatchRequest request) {
    return new CouponPatchCommand(
        request.name(),
        request.discountType(),
        request.discountValue(),
        request.minimumOrderAmount(),
        request.maximumDiscountAmount(),
        request.validFrom(),
        request.validUntil(),
        request.active(),
        request.hasName(),
        request.hasDiscountType(),
        request.hasDiscountValue(),
        request.hasMinimumOrderAmount(),
        request.hasMaximumDiscountAmount(),
        request.hasValidFrom(),
        request.hasValidUntil(),
        request.hasActive());
  }

  private static CouponResponse response(
      com.pawcycle.backend.commerce.coupon.persistence.CouponView view) {
    return new CouponResponse(
        view.couponId(),
        view.name(),
        view.discountType(),
        view.discountValue(),
        view.minimumOrderAmount(),
        view.maximumDiscountAmount(),
        view.validFrom(),
        view.validUntil(),
        view.active());
  }
}
