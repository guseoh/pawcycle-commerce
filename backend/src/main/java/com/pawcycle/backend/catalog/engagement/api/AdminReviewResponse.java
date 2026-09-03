package com.pawcycle.backend.catalog.engagement.api;

import java.time.Instant;

public record AdminReviewResponse(
    Long reviewId,
    Long productId,
    Long memberId,
    int rating,
    String content,
    boolean visible,
    Instant createdAt,
    Instant updatedAt) {}
