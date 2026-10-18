package com.pawcycle.backend.subscription.api;

import com.pawcycle.backend.subscription.model.SubscriptionReadModel;
public record SubscriptionIssueResponse(String code, String message) {
  public static SubscriptionIssueResponse from(SubscriptionReadModel.Issue view) {
    return new SubscriptionIssueResponse(
        view.code(),
        view.message());
  }
}
