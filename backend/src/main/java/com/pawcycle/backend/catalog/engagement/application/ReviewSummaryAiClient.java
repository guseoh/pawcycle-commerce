package com.pawcycle.backend.catalog.engagement.application;

import java.util.List;

public interface ReviewSummaryAiClient {
  String summarize(List<ReviewInput> reviews);

  record ReviewInput(int rating, String content) {}
}
