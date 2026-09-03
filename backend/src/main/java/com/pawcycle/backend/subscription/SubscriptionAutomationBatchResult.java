package com.pawcycle.backend.subscription;

public record SubscriptionAutomationBatchResult(
    int processedCandidates, int ordersCreated, int failures, int duplicateOrNoOp) {}
