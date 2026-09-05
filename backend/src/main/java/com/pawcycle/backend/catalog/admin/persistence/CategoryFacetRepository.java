package com.pawcycle.backend.catalog.admin.persistence;

import com.pawcycle.backend.catalog.admin.domain.CategoryFacetEntity;
import com.pawcycle.backend.catalog.admin.domain.CategoryFacetId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface CategoryFacetRepository extends JpaRepository<CategoryFacetEntity, CategoryFacetId> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select cf from CategoryFacetEntity cf where cf.category.id = :categoryId "
          + "order by cf.facetDefinition.id")
  List<CategoryFacetEntity> findAllForUpdate(@Param("categoryId") Long categoryId);

  @Query(
      "select cf from CategoryFacetEntity cf where cf.category.id = :categoryId "
          + "order by cf.displayOrder, cf.facetDefinition.id")
  List<CategoryFacetEntity> findAllOrdered(@Param("categoryId") Long categoryId);

  Optional<CategoryFacetEntity> findByIdCategoryIdAndIdFacetDefinitionId(
      Long categoryId, Long definitionId);

  @Query("select count(cf) from CategoryFacetEntity cf where cf.category.id = :categoryId")
  long countByCategoryId(@Param("categoryId") Long categoryId);
}
