package com.pawcycle.backend.subscription;

public record SubscriptionIdempotencyCleanupResult(
    int creationRepaired, int commandRepaired, int creationDeleted, int commandDeleted) {}
