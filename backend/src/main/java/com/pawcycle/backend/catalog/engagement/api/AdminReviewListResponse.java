package com.pawcycle.backend.catalog.engagement.api;

import com.pawcycle.backend.catalog.engagement.model.PageView;
import com.pawcycle.backend.catalog.engagement.model.ProductEngagementViews.AdminReviewView;
import java.util.List;

public record AdminReviewListResponse(
    List<AdminReviewResponse> items, int page, int size, long totalElements, int totalPages) {
  public static AdminReviewListResponse from(PageView<AdminReviewView> view) {
    return new AdminReviewListResponse(
        view.items().stream().map(AdminReviewResponse::from).toList(),
        view.page(), view.size(), view.totalElements(), view.totalPages());
  }
}
