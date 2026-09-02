package com.pawcycle.backend.catalog.category.application;

import java.util.List;

public record CategoryListView(List<CategorySummary> items) {
  public record CategorySummary(Long categoryId, String name, String slug) {}
}
