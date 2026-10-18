package com.pawcycle.backend.catalog.engagement.api;

import com.pawcycle.backend.catalog.engagement.model.ProductEngagementViews.QuestionView;
import java.time.Instant;

public record QuestionResponse(
    Long questionId,
    String content,
    String answer,
    boolean answered,
    Instant createdAt,
    Instant updatedAt) {
  public static QuestionResponse from(QuestionView view) {
    if (view == null) return null;
    return new QuestionResponse(
        view.questionId(),
        view.content(),
        view.answer(),
        view.answered(),
        view.createdAt(),
        view.updatedAt());
  }
}
