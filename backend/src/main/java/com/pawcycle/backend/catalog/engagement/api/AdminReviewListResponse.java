package com.pawcycle.backend.catalog.engagement.api;

import java.util.List;

public record AdminReviewListResponse(
    List<AdminReviewResponse> items, int page, int size, long totalElements, int totalPages) {}
