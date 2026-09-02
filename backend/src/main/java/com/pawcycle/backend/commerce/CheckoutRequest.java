package com.pawcycle.backend.commerce;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CheckoutRequest(
    @NotNull @Positive Long addressId, @Positive Long memberCouponId, @Min(0) Long cartVersion) {}
