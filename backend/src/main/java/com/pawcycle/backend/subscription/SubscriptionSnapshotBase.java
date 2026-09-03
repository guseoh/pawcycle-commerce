package com.pawcycle.backend.subscription;

public record SubscriptionSnapshotBase(
    long id, long planVersionId, long packagePriceKrw, int deliveryCycleWeeks) {}
