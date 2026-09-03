package com.pawcycle.backend.subscription;

import com.pawcycle.backend.subscription.api.SubscriptionDetailResponse;

public record SubscriptionResult(
    int status, SubscriptionDetailResponse body, String location, String etag, boolean replay) {}
