package com.pawcycle.backend.subscription.api;

import java.time.LocalDate;
import java.util.List;

public record SubscriptionDetailResponse(
    long subscriptionId,
    String status,
    long version,
    PetResponse pet,
    SubscriptionSnapshotResponse currentSnapshot,
    LocalDate nextScheduledDate,
    SubscriptionSnapshotResponse pendingSnapshot,
    NextDeliveryResponse nextDelivery,
    PendingChangeResponse pendingChange,
    SubscriptionIssueResponse issue,
    List<String> availableActions,
    PageResponse<ScheduleResponse> schedules,
    PageResponse<CommandHistoryResponse> commandHistory) {}
