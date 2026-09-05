package com.pawcycle.backend.commerce.checkout.persistence;

public record CheckoutAddress(
    String recipientName,
    String recipientPhone,
    String postalCode,
    String addressLine1,
    String addressLine2) {}
