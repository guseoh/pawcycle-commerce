package com.pawcycle.backend.commerce.billing.api;

public record BillingRetryResponse(long paymentId, long nextPaymentId, String status) {}
