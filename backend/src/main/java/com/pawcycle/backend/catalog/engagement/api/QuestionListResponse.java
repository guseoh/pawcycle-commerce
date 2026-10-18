package com.pawcycle.backend.catalog.engagement.api;

import com.pawcycle.backend.catalog.engagement.model.PageView;
import com.pawcycle.backend.catalog.engagement.model.ProductEngagementViews.QuestionView;
import java.util.List;

public record QuestionListResponse(
    List<QuestionResponse> items, int page, int size, long totalElements, int totalPages) {
  public static QuestionListResponse from(PageView<QuestionView> view) {
    return new QuestionListResponse(
        view.items().stream().map(QuestionResponse::from).toList(),
        view.page(), view.size(), view.totalElements(), view.totalPages());
  }
}
