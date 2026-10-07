package com.pawcycle.backend.subscription.persistence.projection;

import java.util.List;

public record SubscriptionSnapshot(
    long id, long planVersionId, long packagePriceKrw, int deliveryCycleWeeks, List<SubscriptionItemProjection> items) {}
