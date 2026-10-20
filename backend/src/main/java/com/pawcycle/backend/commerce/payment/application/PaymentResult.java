package com.pawcycle.backend.commerce.payment.application;

public record PaymentResult(long paymentId, long orderId, String status) {}
