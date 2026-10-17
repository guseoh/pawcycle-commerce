package com.pawcycle.backend.catalog.engagement.model;

import java.util.List;

/** Paging metadata and already materialized items; calculations remain in the use case. */
public record PageView<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}
