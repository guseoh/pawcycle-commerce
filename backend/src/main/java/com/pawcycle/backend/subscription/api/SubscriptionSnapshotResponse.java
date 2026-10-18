package com.pawcycle.backend.subscription.api;

import com.pawcycle.backend.subscription.model.SubscriptionReadModel;
import java.util.List;

public record SubscriptionSnapshotResponse(
    long planVersionId,
    long packagePriceKrw,
    int deliveryCycleWeeks,
    List<SubscriptionItemResponse> items) {
  public static SubscriptionSnapshotResponse from(SubscriptionReadModel.Snapshot view) {
    return new SubscriptionSnapshotResponse(
        view.planVersionId(),
        view.packagePriceKrw(),
        view.deliveryCycleWeeks(),
        view.items() == null ? null : view.items().stream()
            .map(value -> value == null ? null : SubscriptionItemResponse.from(value)).toList());
  }
}
