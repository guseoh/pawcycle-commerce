package com.pawcycle.backend.subscription.performance;

public record SubscriptionFixtureSummary(
    int cohortSize, int initialBacklog, int batchSize, long fixedDelayMs) {}
