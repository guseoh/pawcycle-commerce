package com.pawcycle.backend.catalog.engagement.api;

import com.pawcycle.backend.catalog.engagement.application.ProductEngagementService;
import com.pawcycle.backend.catalog.engagement.application.ReviewCreateCommand;
import com.pawcycle.backend.catalog.engagement.application.ReviewPatchCommand;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class ProductReviewController {
  private final ProductEngagementService service;

  @GetMapping("/api/products/{productId}/reviews")
  ReviewListResponse reviews(
      @PathVariable long productId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.reviews(productId, page, size);
  }

  @GetMapping("/api/products/{productId}/reviews/me")
  ReviewResponse myReview(
      @PathVariable long productId,
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal) {
    return service.myReview(productId, principal.memberId());
  }

  @PostMapping("/api/products/{productId}/reviews")
  ReviewResponse create(
      @PathVariable long productId,
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @Valid @RequestBody ReviewCreateRequest request) {
    return service.createReview(
        productId,
        principal.memberId(),
        new ReviewCreateCommand(request.rating(), request.content()));
  }

  @PatchMapping("/api/reviews/{reviewId}")
  ReviewResponse update(
      @PathVariable long reviewId,
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @RequestBody ReviewPatchRequest request) {
    return service.updateReview(
        reviewId,
        principal.memberId(),
        new ReviewPatchCommand(
            request.getRating(),
            request.isRatingPresent(),
            request.getContent(),
            request.isContentPresent()));
  }

  @DeleteMapping("/api/reviews/{reviewId}")
  void delete(
      @PathVariable long reviewId,
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal) {
    service.deleteReview(reviewId, principal.memberId());
  }
}
