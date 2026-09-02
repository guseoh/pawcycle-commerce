package com.pawcycle.backend.catalog.engagement.api;

import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public final class ReviewPatchRequest {
  private Integer rating;
  private boolean ratingPresent;
  private String content;
  private boolean contentPresent;

  @JsonSetter("rating")
  public void readRating(Integer value) {
    rating = value;
    ratingPresent = true;
  }

  @JsonSetter("content")
  public void readContent(String value) {
    content = value;
    contentPresent = true;
  }
}
