package com.pawcycle.backend.commerce.payment.api;

public record PaymentResponse(long paymentId, long orderId, String status) {}
