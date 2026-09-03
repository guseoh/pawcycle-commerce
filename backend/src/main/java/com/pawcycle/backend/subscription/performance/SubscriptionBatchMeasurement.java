package com.pawcycle.backend.subscription.performance;

public record SubscriptionBatchMeasurement(
    int sequence,
    int processed,
    int created,
    int failures,
    int duplicateOrNoOp,
    double durationMs) {}
