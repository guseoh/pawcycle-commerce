package com.pawcycle.backend.catalog.engagement.api;

import com.pawcycle.backend.catalog.engagement.model.ProductEngagementViews.AdminQuestionView;
import java.time.Instant;

public record AdminQuestionResponse(
    Long questionId,
    Long productId,
    Long memberId,
    String content,
    String answer,
    boolean answered,
    boolean visible,
    Instant createdAt,
    Instant updatedAt) {
  public static AdminQuestionResponse from(AdminQuestionView view) {
    if (view == null) return null;
    return new AdminQuestionResponse(
        view.questionId(),
        view.productId(),
        view.memberId(),
        view.content(),
        view.answer(),
        view.answered(),
        view.visible(),
        view.createdAt(),
        view.updatedAt());
  }
}
