package com.pawcycle.backend.subscription.api;

import com.pawcycle.backend.subscription.model.SubscriptionReadModel;
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
    PageResponse<CommandHistoryResponse> commandHistory) {
  public static SubscriptionDetailResponse from(SubscriptionReadModel.Detail view) {
    return new SubscriptionDetailResponse(
        view.subscriptionId(),
        view.status(),
        view.version(),
        view.pet() == null ? null : PetResponse.from(view.pet()),
        view.currentSnapshot() == null ? null : SubscriptionSnapshotResponse.from(view.currentSnapshot()),
        view.nextScheduledDate(),
        view.pendingSnapshot() == null ? null : SubscriptionSnapshotResponse.from(view.pendingSnapshot()),
        view.nextDelivery() == null ? null : NextDeliveryResponse.from(view.nextDelivery()),
        view.pendingChange() == null ? null : PendingChangeResponse.from(view.pendingChange()),
        view.issue() == null ? null : SubscriptionIssueResponse.from(view.issue()),
        view.availableActions(),
        view.schedules() == null ? null : PageResponse.from(view.schedules(), ScheduleResponse::from),
        view.commandHistory() == null ? null : PageResponse.from(view.commandHistory(), CommandHistoryResponse::from));
  }
}
