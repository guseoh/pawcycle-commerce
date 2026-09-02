package com.pawcycle.backend.catalog.engagement.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QuestionAnswerRequest(@NotBlank @Size(max = 10000) String answer) {}
