package com.pawcycle.backend.subscription.model;

/** Creation input; component order and nulls are part of the existing idempotency identity. */
public record SubscriptionCreateCommand(Long petId, Long planVersionId, Integer deliveryCycleWeeks) {}
