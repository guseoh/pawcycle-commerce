package com.pawcycle.backend.catalog.admin.persistence;

import com.pawcycle.backend.catalog.admin.domain.CategoryFacetEntity;
import com.pawcycle.backend.catalog.admin.domain.CategoryFacetId;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

  @Query(
      "select cf from CategoryFacetEntity cf join fetch cf.facetDefinition "
          + "where cf.category.id in :categoryIds "
          + "order by cf.category.id, cf.displayOrder, cf.facetDefinition.id")
  List<CategoryFacetEntity> findAllByCategoryIdsOrdered(
      @Param("categoryIds") List<Long> categoryIds);

  Optional<CategoryFacetEntity> findByIdCategoryIdAndIdFacetDefinitionId(
      Long categoryId, Long definitionId);

  @Query("select count(cf) from CategoryFacetEntity cf where cf.category.id = :categoryId")
  long countByCategoryId(@Param("categoryId") Long categoryId);

  @Modifying
  @Query(
      value =
          """
          INSERT INTO category_facets(category_id,facet_definition_id,display_order)
          VALUES (:categoryId,:definitionId,:displayOrder)
          ON DUPLICATE KEY UPDATE display_order=VALUES(display_order)
          """,
      nativeQuery = true)
  int upsert(
      @Param("categoryId") long categoryId,
      @Param("definitionId") long definitionId,
      @Param("displayOrder") int displayOrder);
}
