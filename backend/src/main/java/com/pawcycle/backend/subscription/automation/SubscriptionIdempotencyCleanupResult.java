package com.pawcycle.backend.subscription.automation;

public record SubscriptionIdempotencyCleanupResult(
    int creationRepaired, int commandRepaired, int creationDeleted, int commandDeleted) {}
