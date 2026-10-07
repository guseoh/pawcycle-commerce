package com.pawcycle.backend.commerce.billing.api;

import jakarta.validation.constraints.NotBlank;

public record BillingCompleteRequest(@NotBlank String prepareToken, @NotBlank String authKey) {}
