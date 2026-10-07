package com.pawcycle.backend.commerce.billing.api;

public record BillingMethodResponse(String provider, boolean configured, boolean registered) {}
