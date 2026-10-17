package com.pawcycle.backend.catalog.engagement.api;

import com.pawcycle.backend.catalog.engagement.model.PageView;
import com.pawcycle.backend.catalog.engagement.model.ProductEngagementViews.AdminQuestionView;
import java.util.List;

public record AdminQuestionListResponse(
    List<AdminQuestionResponse> items, int page, int size, long totalElements, int totalPages) {
  public static AdminQuestionListResponse from(PageView<AdminQuestionView> view) {
    return new AdminQuestionListResponse(
        view.items().stream().map(AdminQuestionResponse::from).toList(),
        view.page(), view.size(), view.totalElements(), view.totalPages());
  }
}
