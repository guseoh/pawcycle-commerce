package com.pawcycle.backend.subscription;

public record CommandHistoryProjection(String commandType, String occurredAt) {}
