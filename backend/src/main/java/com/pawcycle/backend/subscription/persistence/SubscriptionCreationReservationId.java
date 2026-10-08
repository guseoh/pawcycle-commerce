package com.pawcycle.backend.subscription.persistence;

import java.io.Serializable;

public record SubscriptionCreationReservationId(long memberId, String key) implements Serializable {}
