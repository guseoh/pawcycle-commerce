package com.pawcycle.backend.subscription.api;

import com.pawcycle.backend.subscription.model.SubscriptionReadModel;
public record CommandHistoryResponse(String commandType, String result, String occurredAt) {
  public static CommandHistoryResponse from(SubscriptionReadModel.History view) {
    return new CommandHistoryResponse(
        view.commandType(),
        view.result(),
        view.occurredAt());
  }
}
