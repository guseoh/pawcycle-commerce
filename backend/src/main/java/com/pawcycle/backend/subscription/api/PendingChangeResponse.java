package com.pawcycle.backend.subscription.api;

import com.pawcycle.backend.subscription.model.SubscriptionReadModel;
import java.time.LocalDate;
import java.util.List;

public record PendingChangeResponse(
    long targetScheduleId,
    LocalDate appliesOn,
    long planVersionId,
    long packagePriceKrw,
    int deliveryCycleWeeks,
    List<SubscriptionItemDetailResponse> items) {
  public static PendingChangeResponse from(SubscriptionReadModel.PendingChange view) {
    return new PendingChangeResponse(
        view.targetScheduleId(),
        view.appliesOn(),
        view.planVersionId(),
        view.packagePriceKrw(),
        view.deliveryCycleWeeks(),
        view.items() == null ? null : view.items().stream()
            .map(value -> value == null ? null : SubscriptionItemDetailResponse.from(value)).toList());
  }
}
