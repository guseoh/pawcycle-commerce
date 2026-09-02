package com.pawcycle.backend.catalog.engagement.api;

import java.time.Instant;
import java.util.List;

public final class ReviewViews {
  private ReviewViews() {}

  public record Page(List<Review> items, int page, int size, long totalElements, int totalPages) {}

  public record Review(
      Long reviewId, int rating, String content, Instant createdAt, Instant updatedAt) {}

  public record AdminPage(
      List<AdminReview> items, int page, int size, long totalElements, int totalPages) {}

  public record AdminReview(
      Long reviewId,
      Long productId,
      Long memberId,
      int rating,
      String content,
      boolean visible,
      Instant createdAt,
      Instant updatedAt) {}
}
