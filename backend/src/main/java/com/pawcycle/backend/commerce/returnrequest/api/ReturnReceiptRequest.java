package com.pawcycle.backend.commerce.returnrequest.api;

import jakarta.validation.constraints.NotNull;

public record ReturnReceiptRequest(@NotNull Boolean restock) {}
