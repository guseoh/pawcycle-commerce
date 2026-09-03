package com.pawcycle.backend.catalog.engagement.api;

import java.util.List;

public record ReviewListResponse(
    List<ReviewResponse> items, int page, int size, long totalElements, int totalPages) {}
