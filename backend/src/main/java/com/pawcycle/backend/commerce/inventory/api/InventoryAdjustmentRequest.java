package com.pawcycle.backend.commerce.inventory.api;

import jakarta.validation.constraints.NotNull;

public record InventoryAdjustmentRequest(@NotNull Integer delta) {}
