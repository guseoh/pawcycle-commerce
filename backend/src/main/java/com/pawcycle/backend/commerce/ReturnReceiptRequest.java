package com.pawcycle.backend.commerce;

import jakarta.validation.constraints.NotNull;

public record ReturnReceiptRequest(@NotNull Boolean restock) {}
