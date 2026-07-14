package com.pawcycle.backend.subscription.application;

import java.time.LocalDate;

public record SubscriptionCreateResult(Long subscriptionId, LocalDate nextOrderDate) {
}
