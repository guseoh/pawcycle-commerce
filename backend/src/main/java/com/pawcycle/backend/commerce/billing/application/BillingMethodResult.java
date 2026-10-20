package com.pawcycle.backend.commerce.billing.application;

public record BillingMethodResult(String provider, boolean configured, boolean registered) {}
