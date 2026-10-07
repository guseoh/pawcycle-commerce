package com.pawcycle.backend.subscription.migration;

public record LegacySubscriptionPreflight(boolean valid, int invalidRows) {}
