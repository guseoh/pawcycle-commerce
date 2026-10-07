package com.pawcycle.backend.subscription.persistence.projection;

public record SubscriptionSnapshotBase(
    long id, long planVersionId, long packagePriceKrw, int deliveryCycleWeeks) {}
