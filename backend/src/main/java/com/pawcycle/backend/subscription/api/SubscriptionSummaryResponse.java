package com.pawcycle.backend.subscription.api;

import com.pawcycle.backend.subscription.model.SubscriptionReadModel;
import java.time.LocalDate;

public record SubscriptionSummaryResponse(
    long subscriptionId,
    String status,
    long version,
    PetResponse pet,
    SubscriptionSnapshotResponse currentSnapshot,
    LocalDate nextScheduledDate) {
  public static SubscriptionSummaryResponse from(SubscriptionReadModel.Summary view) {
    return new SubscriptionSummaryResponse(
        view.subscriptionId(),
        view.status(),
        view.version(),
        view.pet() == null ? null : PetResponse.from(view.pet()),
        view.currentSnapshot() == null ? null : SubscriptionSnapshotResponse.from(view.currentSnapshot()),
        view.nextScheduledDate());
  }
}
