package com.pawcycle.backend.subscription.persistence;

import java.io.Serializable;

public record SubscriptionOrderAddonItemId(long orderId, long skuId) implements Serializable {}
