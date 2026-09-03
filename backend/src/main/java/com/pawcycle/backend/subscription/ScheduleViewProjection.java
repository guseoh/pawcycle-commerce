package com.pawcycle.backend.subscription;

import java.time.LocalDate;

public record ScheduleViewProjection(
    long id, LocalDate scheduledDate, String status, Long effectiveSnapshotId) {}
