package com.pawcycle.backend.subscription.application;

import com.pawcycle.backend.subscription.model.SubscriptionReadModel;

/** Typed application result; HTTP status and headers are assembled only by the controller. */
public record SubscriptionOperationResult(
    int status, SubscriptionReadModel.Detail body, String location, String etag, boolean replay) {}
