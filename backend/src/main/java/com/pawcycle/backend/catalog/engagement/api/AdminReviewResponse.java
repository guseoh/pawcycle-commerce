package com.pawcycle.backend.catalog.engagement.api;

import com.pawcycle.backend.catalog.engagement.model.ProductEngagementViews.AdminReviewView;
import java.time.Instant;

public record AdminReviewResponse(
    Long reviewId,
    Long productId,
    Long memberId,
    int rating,
    String content,
    boolean visible,
    Instant createdAt,
    Instant updatedAt) {
  public static AdminReviewResponse from(AdminReviewView view) {
    if (view == null) return null;
    return new AdminReviewResponse(
        view.reviewId(),
        view.productId(),
        view.memberId(),
        view.rating(),
        view.content(),
        view.visible(),
        view.createdAt(),
        view.updatedAt());
  }
}
