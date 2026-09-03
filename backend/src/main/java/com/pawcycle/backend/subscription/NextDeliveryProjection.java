package com.pawcycle.backend.subscription;

import java.time.LocalDate;

public record NextDeliveryProjection(
    long id,
    LocalDate scheduledDate,
    String status,
    String holdReason,
    Long effectiveSnapshotId) {}
