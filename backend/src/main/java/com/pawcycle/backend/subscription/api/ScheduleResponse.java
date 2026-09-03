package com.pawcycle.backend.subscription.api;

import java.time.LocalDate;

public record ScheduleResponse(
    long scheduleId, LocalDate scheduledDate, String status, Long effectiveSnapshotId) {}
