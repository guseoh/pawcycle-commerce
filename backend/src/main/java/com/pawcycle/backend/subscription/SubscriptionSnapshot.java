package com.pawcycle.backend.subscription;

import java.util.List;

public record SubscriptionSnapshot(
    long id, long planVersionId, long packagePriceKrw, int deliveryCycleWeeks, List<SubscriptionItemProjection> items) {}
