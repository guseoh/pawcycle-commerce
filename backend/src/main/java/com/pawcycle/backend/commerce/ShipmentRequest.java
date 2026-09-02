package com.pawcycle.backend.commerce;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShipmentRequest(
    @NotBlank @Size(max = 50) String carrierCode,
    @NotBlank @Size(max = 100) String trackingNumber) {}
