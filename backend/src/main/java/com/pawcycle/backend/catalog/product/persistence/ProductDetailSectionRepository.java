package com.pawcycle.backend.catalog.product.persistence;

import com.pawcycle.backend.catalog.product.domain.ProductDetailSectionEntity;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductDetailSectionRepository extends JpaRepository<ProductDetailSectionEntity, Long> {
  @Query(
      "select new com.pawcycle.backend.catalog.product.persistence.ProductDetailSectionRow("
          + "section.id, section.title, section.body, section.displayOrder, section.visible,"
          + "section.createdAt, section.updatedAt)"
          + " from ProductDetailSectionEntity section"
          + " where section.productId = :productId and section.visible = true"
          + " order by section.displayOrder asc, section.id asc")
  List<ProductDetailSectionRow> findVisibleByProductId(@Param("productId") long productId);

  @Query(
      "select section from ProductDetailSectionEntity section where section.productId = :productId "
          + "order by section.displayOrder asc, section.id asc")
  List<ProductDetailSectionEntity> findAllByProductId(@Param("productId") long productId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select section from ProductDetailSectionEntity section where section.id = :sectionId "
          + "and section.productId = :productId")
  Optional<ProductDetailSectionEntity> findByIdAndProductIdForUpdate(
      @Param("sectionId") long sectionId, @Param("productId") long productId);

  Optional<ProductDetailSectionEntity> findByIdAndProductId(Long sectionId, Long productId);
}
