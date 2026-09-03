package com.pawcycle.backend.catalog.engagement.api;

import java.time.Instant;

public record AdminQuestionResponse(
    Long questionId,
    Long productId,
    Long memberId,
    String content,
    String answer,
    boolean answered,
    boolean visible,
    Instant createdAt,
    Instant updatedAt) {}
