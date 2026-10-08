package com.pawcycle.backend.subscription.persistence;

import java.io.Serializable;

public record SubscriptionOrderItemId(long orderId, long skuId) implements Serializable {}
