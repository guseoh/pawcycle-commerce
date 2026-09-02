package com.pawcycle.backend.commerce;

import jakarta.validation.constraints.NotBlank;

public record BillingCompleteRequest(@NotBlank String prepareToken, @NotBlank String authKey) {}
