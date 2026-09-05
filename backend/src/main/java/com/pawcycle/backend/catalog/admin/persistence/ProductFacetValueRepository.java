package com.pawcycle.backend.catalog.admin.persistence;

import com.pawcycle.backend.catalog.admin.domain.ProductFacetValueEntity;
import com.pawcycle.backend.catalog.admin.domain.ProductFacetValueId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductFacetValueRepository extends JpaRepository<ProductFacetValueEntity, ProductFacetValueId> {
  @Modifying
  @Query("delete from ProductFacetValueEntity pfv where pfv.product.id = :productId")
  int deleteAllByProductId(@Param("productId") Long productId);

  @Query(
      "select pfv.id.facetOptionId from ProductFacetValueEntity pfv "
          + "join pfv.facetOption fo join fo.facetDefinition fd "
          + "left join CategoryFacetEntity cf on cf.category.id = pfv.product.category.id "
          + "and cf.facetDefinition.id = fd.id where pfv.product.id = :productId "
          + "order by case when cf.displayOrder is null then 1 else 0 end, "
          + "cf.displayOrder, fd.id, fo.displayOrder, fo.id")
  List<Long> findOptionIdsOrdered(@Param("productId") Long productId);

  @Query(
      "select count(pfv) from ProductFacetValueEntity pfv "
          + "where pfv.product.category.id = :categoryId "
          + "and pfv.facetOption.facetDefinition.id = :definitionId")
  long countByCategoryAndDefinition(
      @Param("categoryId") Long categoryId, @Param("definitionId") Long definitionId);

  @Query(
      "select count(pfv) from ProductFacetValueEntity pfv "
          + "where pfv.product.id = :productId and "
          + "pfv.facetOption.facetDefinition.id = :definitionId")
  long countByProductAndDefinition(
      @Param("productId") Long productId, @Param("definitionId") Long definitionId);

  @Query(
      "select count(pfv) from ProductFacetValueEntity pfv "
          + "where pfv.product.id = :productId and not exists "
          + "(select cf.id from CategoryFacetEntity cf "
          + "where cf.category.id = :categoryId "
          + "and cf.facetDefinition.id = pfv.facetOption.facetDefinition.id)")
  long countIncompatible(
      @Param("productId") Long productId, @Param("categoryId") Long categoryId);
}
