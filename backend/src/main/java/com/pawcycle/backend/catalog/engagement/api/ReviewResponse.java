package com.pawcycle.backend.catalog.engagement.api;

import java.time.Instant;

public record ReviewResponse(
    Long reviewId, int rating, String content, Instant createdAt, Instant updatedAt) {}
