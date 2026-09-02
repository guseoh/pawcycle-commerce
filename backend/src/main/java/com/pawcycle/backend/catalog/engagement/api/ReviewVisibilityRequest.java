package com.pawcycle.backend.catalog.engagement.api;

import jakarta.validation.constraints.NotNull;

public record ReviewVisibilityRequest(@NotNull Boolean visible) {}
