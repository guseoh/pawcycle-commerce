package com.pawcycle.backend.subscription;

import java.util.List;

public record PageProjection<T>(int page, int size, long total, List<T> items) {}
