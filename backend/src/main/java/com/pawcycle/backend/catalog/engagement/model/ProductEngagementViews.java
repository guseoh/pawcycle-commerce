package com.pawcycle.backend.catalog.engagement.model;

/** Materialized engagement results, independent of HTTP and persistence. */
public final class ProductEngagementViews {
  private ProductEngagementViews() {}

  public record ReviewView(
      Long reviewId,
      int rating,
      String content,
      java.time.Instant createdAt,
      java.time.Instant updatedAt) {}

  public record AdminReviewView(
      Long reviewId,
      Long productId,
      Long memberId,
      int rating,
      String content,
      boolean visible,
      java.time.Instant createdAt,
      java.time.Instant updatedAt) {}

  public record QuestionView(
      Long questionId,
      String content,
      String answer,
      boolean answered,
      java.time.Instant createdAt,
      java.time.Instant updatedAt) {}

  public record AdminQuestionView(
      Long questionId,
      Long productId,
      Long memberId,
      String content,
      String answer,
      boolean answered,
      boolean visible,
      java.time.Instant createdAt,
      java.time.Instant updatedAt) {}
}
