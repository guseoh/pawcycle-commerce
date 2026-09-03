package com.pawcycle.backend.catalog.engagement.api;

import java.util.List;

public record AdminQuestionListResponse(
    List<AdminQuestionResponse> items, int page, int size, long totalElements, int totalPages) {}
