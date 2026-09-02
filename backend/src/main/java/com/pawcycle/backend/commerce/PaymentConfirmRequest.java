package com.pawcycle.backend.commerce;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PaymentConfirmRequest(
    @NotBlank String paymentKey,
    @NotBlank String providerOrderId,
    @NotNull @DecimalMin("0.00") BigDecimal amount) {}
