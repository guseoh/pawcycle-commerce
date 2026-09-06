package com.pawcycle.backend.catalog.category.persistence;

import com.pawcycle.backend.catalog.category.domain.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoryRepository extends JpaRepository<Category, Long> {

  List<Category> findAllByOrderByDisplayOrderAscIdAsc();

  @Query(
      "select category from Category category left join fetch category.parent "
          + "where category.active = true order by category.displayOrder, category.id")
  List<Category> findAllByActiveTrueOrderByDisplayOrderAscIdAsc();

  boolean existsBySlug(String slug);

  Optional<Category> findBySlug(String slug);

  boolean existsBySlugAndIdNot(String slug, Long id);
}
