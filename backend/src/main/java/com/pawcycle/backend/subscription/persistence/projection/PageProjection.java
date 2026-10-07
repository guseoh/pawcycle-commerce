package com.pawcycle.backend.subscription.persistence.projection;

import java.util.List;

public record PageProjection<T>(int page, int size, long total, List<T> items) {}
