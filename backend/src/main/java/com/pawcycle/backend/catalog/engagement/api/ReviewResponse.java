package com.pawcycle.backend.catalog.engagement.api;

import com.pawcycle.backend.catalog.engagement.model.ProductEngagementViews.ReviewView;
import java.time.Instant;

public record ReviewResponse(
    Long reviewId, int rating, String content, Instant createdAt, Instant updatedAt) {
  public static ReviewResponse from(ReviewView view) {
    if (view == null) return null;
    return new ReviewResponse(
        view.reviewId(), view.rating(), view.content(), view.createdAt(), view.updatedAt());
  }
}
