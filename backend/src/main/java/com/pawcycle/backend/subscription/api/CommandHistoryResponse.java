package com.pawcycle.backend.subscription.api;

public record CommandHistoryResponse(String commandType, String result, String occurredAt) {}
