package com.pawcycle.backend.catalog.engagement.api;

import java.time.Instant;
import java.util.List;

public final class QuestionViews {
  private QuestionViews() {}

  public record Page(
      List<Question> items, int page, int size, long totalElements, int totalPages) {}

  public record Question(
      Long questionId,
      String content,
      String answer,
      boolean answered,
      Instant createdAt,
      Instant updatedAt) {}

  public record AdminPage(
      List<AdminQuestion> items, int page, int size, long totalElements, int totalPages) {}

  public record AdminQuestion(
      Long questionId,
      Long productId,
      Long memberId,
      String content,
      String answer,
      boolean answered,
      boolean visible,
      Instant createdAt,
      Instant updatedAt) {}
}
