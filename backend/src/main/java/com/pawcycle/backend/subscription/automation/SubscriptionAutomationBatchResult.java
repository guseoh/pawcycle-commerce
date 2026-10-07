package com.pawcycle.backend.subscription.automation;

public record SubscriptionAutomationBatchResult(
    int processedCandidates, int ordersCreated, int failures, int duplicateOrNoOp) {}
