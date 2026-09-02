package com.pawcycle.backend.catalog.engagement.api;

import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public final class QuestionPatchRequest {
  private String content;
  private boolean contentPresent;

  @JsonSetter("content")
  public void readContent(String value) {
    content = value;
    contentPresent = true;
  }
}
