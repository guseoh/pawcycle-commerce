package com.pawcycle.backend.commerce.returning.api;

import jakarta.validation.constraints.NotNull;

public record ReturnReceiptRequest(@NotNull Boolean restock) {}
