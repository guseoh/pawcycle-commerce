package com.pawcycle.backend.subscription.persistence.projection;

import java.time.LocalDate;

public record NextDeliveryProjection(
    long id,
    LocalDate scheduledDate,
    String status,
    String holdReason,
    Long effectiveSnapshotId) {}
