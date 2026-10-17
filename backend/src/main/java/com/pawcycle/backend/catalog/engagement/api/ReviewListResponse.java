package com.pawcycle.backend.catalog.engagement.api;

import com.pawcycle.backend.catalog.engagement.model.PageView;
import com.pawcycle.backend.catalog.engagement.model.ProductEngagementViews.ReviewView;
import java.util.List;

public record ReviewListResponse(
    List<ReviewResponse> items, int page, int size, long totalElements, int totalPages) {
  public static ReviewListResponse from(PageView<ReviewView> view) {
    return new ReviewListResponse(
        view.items().stream().map(ReviewResponse::from).toList(),
        view.page(), view.size(), view.totalElements(), view.totalPages());
  }
}
