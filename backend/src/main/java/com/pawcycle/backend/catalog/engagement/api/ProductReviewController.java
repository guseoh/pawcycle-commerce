package com.pawcycle.backend.catalog.engagement.api;

import com.pawcycle.backend.catalog.engagement.application.ProductEngagementService;
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
    ReviewViews.Page reviews(@PathVariable long productId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return service.reviews(productId, page, size); }

    @GetMapping("/api/products/{productId}/reviews/me")
    ReviewViews.Review myReview(@PathVariable long productId, @AuthenticationPrincipal AuthenticatedMemberPrincipal principal) {
        return service.myReview(productId, principal.memberId());
    }

    @PostMapping("/api/products/{productId}/reviews")
    ReviewViews.Review create(@PathVariable long productId, @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
            @Valid @RequestBody EngagementRequests.ReviewCreate request) {
        return service.createReview(productId, principal.memberId(), request);
    }

    @PatchMapping("/api/reviews/{reviewId}")
    ReviewViews.Review update(@PathVariable long reviewId, @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
            @RequestBody EngagementRequests.ReviewPatch request) {
        return service.updateReview(reviewId, principal.memberId(), request);
    }

    @DeleteMapping("/api/reviews/{reviewId}")
    void delete(@PathVariable long reviewId, @AuthenticationPrincipal AuthenticatedMemberPrincipal principal) {
        service.deleteReview(reviewId, principal.memberId());
    }
}
