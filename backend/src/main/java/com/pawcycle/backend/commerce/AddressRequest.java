package com.pawcycle.backend.commerce;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(
    @Size(max = 100) String name,
    @NotBlank @Size(max = 100) String recipientName,
    @NotBlank @Size(max = 30) String recipientPhone,
    @NotBlank @Size(max = 20) String postalCode,
    @NotBlank @Size(max = 255) String addressLine1,
    @Size(max = 255) String addressLine2) {}
