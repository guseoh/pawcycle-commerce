package com.pawcycle.backend.subscription.api;

import com.pawcycle.backend.subscription.model.SubscriptionReadModel;
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
    BigDecimal orderTotalKrw) {
  public static NextDeliveryResponse from(SubscriptionReadModel.NextDelivery view) {
    return new NextDeliveryResponse(
        view.scheduleId(),
        view.scheduledDate(),
        view.status(),
        view.planVersionId(),
        view.packagePriceKrw(),
        view.deliveryCycleWeeks(),
        view.items() == null ? null : view.items().stream()
            .map(value -> value == null ? null : SubscriptionItemDetailResponse.from(value)).toList(),
        view.addOns() == null ? null : view.addOns().stream()
            .map(value -> value == null ? null : SubscriptionAddonResponse.from(value)).toList(),
        view.addOnTotalKrw(),
        view.orderTotalKrw());
  }
}
