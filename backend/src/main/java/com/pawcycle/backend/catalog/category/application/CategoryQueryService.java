package com.pawcycle.backend.catalog.category.application;

import com.pawcycle.backend.catalog.category.infra.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryQueryService {
	private final CategoryRepository categoryRepository;

	@Transactional(readOnly = true)
	public CategoryListView findPublicCategories() {
		return new CategoryListView(categoryRepository.findAllByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
				.map(category -> new CategoryListView.CategorySummary(
						category.getId(), category.getName(), category.getSlug()))
				.toList());
	}
}
