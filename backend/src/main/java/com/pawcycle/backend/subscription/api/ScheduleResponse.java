package com.pawcycle.backend.subscription.api;

import com.pawcycle.backend.subscription.model.SubscriptionReadModel;
import java.time.LocalDate;

public record ScheduleResponse(
    long scheduleId, LocalDate scheduledDate, String status, Long effectiveSnapshotId) {
  public static ScheduleResponse from(SubscriptionReadModel.Schedule view) {
    return new ScheduleResponse(
        view.scheduleId(),
        view.scheduledDate(),
        view.status(),
        view.effectiveSnapshotId());
  }
}
