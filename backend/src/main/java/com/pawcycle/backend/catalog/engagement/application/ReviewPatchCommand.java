package com.pawcycle.backend.catalog.engagement.application;

public record ReviewPatchCommand(
    Integer rating, boolean ratingPresent, String content, boolean contentPresent) {}
