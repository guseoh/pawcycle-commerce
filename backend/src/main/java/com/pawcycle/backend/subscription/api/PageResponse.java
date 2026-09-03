package com.pawcycle.backend.subscription.api;

import java.util.List;

public record PageResponse<T>(int page, int size, long totalElements, List<T> items) {}
