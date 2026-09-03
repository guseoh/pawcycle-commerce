package com.pawcycle.backend.subscription.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record NextDeliveryResponse(
    long scheduleId,
    LocalDate scheduledDate,
    String status,
    long planVersionId,
    long packagePriceKrw,
    int deliveryCycleWeeks,
    List<SubscriptionItemDetailResponse> items,
    List<SubscriptionAddonResponse> addOns,
    BigDecimal addOnTotalKrw,
    BigDecimal orderTotalKrw) {}
