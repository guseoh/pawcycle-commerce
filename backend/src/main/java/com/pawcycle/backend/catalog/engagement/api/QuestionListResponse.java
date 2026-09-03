package com.pawcycle.backend.catalog.engagement.api;

import java.util.List;

public record QuestionListResponse(
    List<QuestionResponse> items, int page, int size, long totalElements, int totalPages) {}
