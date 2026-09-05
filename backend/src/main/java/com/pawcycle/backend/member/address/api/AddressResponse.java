package com.pawcycle.backend.member.address.api;

public record AddressResponse(
    long addressId,
    String name,
    String recipientName,
    String recipientPhone,
    String postalCode,
    String addressLine1,
    String addressLine2,
    boolean isDefault) {}
