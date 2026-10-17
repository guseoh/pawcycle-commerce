package com.pawcycle.backend.catalog.engagement.api;

import com.pawcycle.backend.catalog.engagement.model.ProductEngagementViews.ReviewView;
import com.pawcycle.backend.catalog.engagement.model.ProductEngagementViews.AdminReviewView;
import com.pawcycle.backend.catalog.engagement.model.ProductEngagementViews.QuestionView;
import com.pawcycle.backend.catalog.engagement.model.ProductEngagementViews.AdminQuestionView;

/** Frozen response constructor arguments from main 04b29c06. */
final class FrozenEngagementResponseMapping {
  private FrozenEngagementResponseMapping() {}

  static ReviewResponse response(ReviewView view) {
    if (view == null) return null;
    return new ReviewResponse(
        view.reviewId(), view.rating(), view.content(), view.createdAt(), view.updatedAt());
  }

  static AdminReviewResponse response(AdminReviewView view) {
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

  static QuestionResponse response(QuestionView view) {
    if (view == null) return null;
    return new QuestionResponse(
        view.questionId(),
        view.content(),
        view.answer(),
        view.answered(),
        view.createdAt(),
        view.updatedAt());
  }

  static AdminQuestionResponse response(
      AdminQuestionView view) {
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
