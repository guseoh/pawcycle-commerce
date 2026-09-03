package com.pawcycle.backend.subscription.performance;

import java.util.List;

public record SubscriptionDrainSummary(
    int initialBacklog,
    int finalBacklog,
    int batchCount,
    int processed,
    int created,
    int failures,
    int duplicateOrNoOp,
    double rawDrainElapsedMs,
    double ordersPerSecond,
    double batchDurationP50Ms,
    double batchDurationP95Ms,
    double batchDurationMaxMs,
    int defaultSchedulerBatchSize,
    long defaultSchedulerFixedDelayMs,
    int defaultSchedulerProjectedTicks,
    long defaultSchedulerProjectedCompletionMs,
    String projectionBasis,
    int databaseOrderCount,
    int duplicateScheduleOrderCount,
    int futureScheduleCount,
    boolean harnessFailure,
    List<SubscriptionBatchMeasurement> batches) {}
