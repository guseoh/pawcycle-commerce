package com.pawcycle.backend.subscription.persistence;

import java.io.Serializable;

public record SubscriptionScheduleAddonId(long scheduleId, long skuId) implements Serializable {}
