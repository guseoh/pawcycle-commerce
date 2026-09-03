package com.pawcycle.backend.subscription;

public record StoredIdempotencyResult(
    String fingerprint, int status, String bodyJson, String location, String etag) {}
