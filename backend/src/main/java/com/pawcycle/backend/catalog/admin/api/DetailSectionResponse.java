package com.pawcycle.backend.catalog.admin.api;

import java.time.Instant;

public record DetailSectionResponse(
    Long sectionId,
    Long productId,
    String title,
    String body,
    int displayOrder,
    boolean visible,
    Instant createdAt,
    Instant updatedAt) {}
