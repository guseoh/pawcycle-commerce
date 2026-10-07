package com.pawcycle.backend.subscription.persistence.projection;

public record CommandHistoryProjection(String commandType, String occurredAt) {}
