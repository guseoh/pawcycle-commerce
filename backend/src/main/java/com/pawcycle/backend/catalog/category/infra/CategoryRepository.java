package com.pawcycle.backend.catalog.category.infra;

import com.pawcycle.backend.catalog.category.domain.Category;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

	List<Category> findAllByOrderByDisplayOrderAscIdAsc();

	boolean existsBySlug(String slug);

	boolean existsBySlugAndIdNot(String slug, Long id);
}
