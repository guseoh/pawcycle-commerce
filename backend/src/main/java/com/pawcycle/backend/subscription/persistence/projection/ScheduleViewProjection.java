package com.pawcycle.backend.subscription.persistence.projection;

import java.time.LocalDate;

public record ScheduleViewProjection(
    long id, LocalDate scheduledDate, String status, Long effectiveSnapshotId) {}
