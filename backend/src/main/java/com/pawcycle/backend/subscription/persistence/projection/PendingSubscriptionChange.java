package com.pawcycle.backend.subscription.persistence.projection;

import java.time.LocalDate;

public record PendingSubscriptionChange(
    long snapshotId, long targetScheduleId, LocalDate targetScheduledDate) {}
