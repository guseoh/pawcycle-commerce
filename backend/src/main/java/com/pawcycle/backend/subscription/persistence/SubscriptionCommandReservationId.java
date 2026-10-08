package com.pawcycle.backend.subscription.persistence;

import java.io.Serializable;

public record SubscriptionCommandReservationId(long memberId, long subscriptionId, String command, String key) implements Serializable {}
