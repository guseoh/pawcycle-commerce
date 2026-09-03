package com.pawcycle.backend.subscription.api;

import java.time.LocalDate;
import java.util.List;

public record PendingChangeResponse(
    long targetScheduleId,
    LocalDate appliesOn,
    long planVersionId,
    long packagePriceKrw,
    int deliveryCycleWeeks,
    List<SubscriptionItemDetailResponse> items) {}
