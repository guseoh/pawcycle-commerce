package com.pawcycle.backend.catalog.engagement.api;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

public final class EngagementRequests {
    private EngagementRequests() {}

    public record ReviewCreate(@NotNull @Min(1) @Max(5) Integer rating,
                               @NotBlank @Size(max = 10000) String content) {}

    @Getter
    @NoArgsConstructor
    public static final class ReviewPatch {
        private Integer rating;
        private boolean ratingPresent;
        private String content;
        private boolean contentPresent;
        @JsonSetter("rating") public void readRating(Integer value) { rating = value; ratingPresent = true; }
        @JsonSetter("content") public void readContent(String value) { content = value; contentPresent = true; }
    }

    public record VisibilityPatch(@NotNull Boolean visible) {}

    public record QuestionCreate(@NotBlank @Size(max = 10000) String content) {}

    @Getter
    @NoArgsConstructor
    public static final class QuestionPatch {
        private String content;
        private boolean contentPresent;
        @JsonSetter("content") public void readContent(String value) { content = value; contentPresent = true; }
    }

    public record AnswerRequest(@NotBlank @Size(max = 10000) String answer) {}
}
