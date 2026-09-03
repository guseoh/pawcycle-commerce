package com.pawcycle.backend.subscription;

import java.time.LocalDate;

public record PendingSubscriptionChange(
    long snapshotId, long targetScheduleId, LocalDate targetScheduledDate) {}
