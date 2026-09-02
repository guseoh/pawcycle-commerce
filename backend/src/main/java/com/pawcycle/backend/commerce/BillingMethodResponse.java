package com.pawcycle.backend.commerce;

public record BillingMethodResponse(String provider, boolean configured, boolean registered) {}
