package com.pawcycle.backend.subscription.api;

import com.pawcycle.backend.subscription.model.SubscriptionReadModel;
import java.time.LocalDate;

public record PlanSaleResponse(boolean onSale, LocalDate startsOn, LocalDate endsOn) {
  public static PlanSaleResponse from(SubscriptionReadModel.PlanSale value) {
    return new PlanSaleResponse(value.onSale(), value.startsOn(), value.endsOn());
  }
}
