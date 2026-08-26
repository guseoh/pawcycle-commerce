package com.pawcycle.backend.catalog.product.application;

import java.time.Instant;

public record ProductDetailSectionView(
        Long sectionId,
        String title,
        String body,
        int displayOrder,
        boolean visible,
        Instant createdAt,
        Instant updatedAt) {}
