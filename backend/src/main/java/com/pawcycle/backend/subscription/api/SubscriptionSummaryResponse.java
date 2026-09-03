package com.pawcycle.backend.subscription.api;

import java.time.LocalDate;

public record SubscriptionSummaryResponse(
    long subscriptionId,
    String status,
    long version,
    PetResponse pet,
    SubscriptionSnapshotResponse currentSnapshot,
    LocalDate nextScheduledDate) {}
