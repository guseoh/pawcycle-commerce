package com.pawcycle.backend.commerce;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CartItemRequest(@NotNull @Positive Long skuId, @NotNull @Positive Integer quantity) {}
