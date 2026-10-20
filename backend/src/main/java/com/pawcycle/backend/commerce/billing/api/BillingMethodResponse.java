package com.pawcycle.backend.commerce.billing.api;

import com.pawcycle.backend.commerce.billing.application.BillingMethodResult;

public record BillingMethodResponse(String provider, boolean configured, boolean registered) {
  public static BillingMethodResponse from(BillingMethodResult model) {
    return new BillingMethodResponse(model.provider(), model.configured(), model.registered());
  }
}
