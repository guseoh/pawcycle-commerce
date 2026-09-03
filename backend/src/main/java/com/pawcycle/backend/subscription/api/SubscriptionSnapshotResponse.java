package com.pawcycle.backend.subscription.api;

import java.util.List;

public record SubscriptionSnapshotResponse(
    long planVersionId,
    long packagePriceKrw,
    int deliveryCycleWeeks,
    List<SubscriptionItemResponse> items) {}
