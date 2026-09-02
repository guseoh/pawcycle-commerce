package com.pawcycle.backend.commerce;

public record BillingRetryResponse(long paymentId, long nextPaymentId, String status) {}
