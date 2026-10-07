package com.pawcycle.backend.subscription.persistence.projection;

public record StoredIdempotencyResult(
    String fingerprint, int status, String bodyJson, String location, String etag) {}
