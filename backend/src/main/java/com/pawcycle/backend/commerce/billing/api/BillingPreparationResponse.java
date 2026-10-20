package com.pawcycle.backend.commerce.billing.api;

import com.pawcycle.backend.commerce.billing.application.BillingPreparationResult;

public record BillingPreparationResponse(String prepareToken) {
  public static BillingPreparationResponse from(BillingPreparationResult model) {
    return new BillingPreparationResponse(model.prepareToken());
  }
}
