package com.pawcycle.backend.subscription.application;

import com.pawcycle.backend.subscription.model.SubscriptionReadModel;

public record SubscriptionResult(
    int status, SubscriptionReadModel.Detail body, String location, String etag, boolean replay) {}
