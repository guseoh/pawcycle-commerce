package com.pawcycle.backend.catalog.category.api;

import com.pawcycle.backend.catalog.category.application.CategoryListView;
import com.pawcycle.backend.catalog.category.application.CategoryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {
  private final CategoryQueryService categoryQueryService;

  @GetMapping
  CategoryListView categories() {
    return categoryQueryService.findPublicCategories();
  }
}
