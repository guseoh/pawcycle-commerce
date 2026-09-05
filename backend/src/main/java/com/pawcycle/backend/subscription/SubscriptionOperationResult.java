package com.pawcycle.backend.subscription;

import com.pawcycle.backend.subscription.api.SubscriptionDetailResponse;

/** Typed application result; HTTP status and headers are assembled only by the controller. */
public record SubscriptionOperationResult(
    int status, SubscriptionDetailResponse body, String location, String etag, boolean replay) {}
