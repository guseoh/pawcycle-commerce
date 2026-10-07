package com.pawcycle.backend.subscription.persistence.projection;

public record SubscriptionProjection(
    long id,
    long memberId,
    String status,
    long version,
    Long petId,
    int deliveryCycleWeeks,
    long currentSnapshotId) {}
