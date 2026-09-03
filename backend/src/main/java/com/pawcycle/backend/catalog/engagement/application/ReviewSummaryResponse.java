package com.pawcycle.backend.catalog.engagement.application;

import java.math.BigDecimal;

public record ReviewSummaryResponse(
    String status, String summary, long reviewCount, BigDecimal averageRating) {}
