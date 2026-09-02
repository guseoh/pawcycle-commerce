package com.pawcycle.backend.commerce;

import jakarta.validation.constraints.NotNull;

public record InventoryAdjustmentRequest(@NotNull Integer delta) {}
