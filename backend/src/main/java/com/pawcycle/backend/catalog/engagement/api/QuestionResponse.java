package com.pawcycle.backend.catalog.engagement.api;

import java.time.Instant;

public record QuestionResponse(
    Long questionId,
    String content,
    String answer,
    boolean answered,
    Instant createdAt,
    Instant updatedAt) {}
