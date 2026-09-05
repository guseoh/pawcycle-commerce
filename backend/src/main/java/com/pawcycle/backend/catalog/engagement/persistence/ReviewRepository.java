package com.pawcycle.backend.catalog.engagement.persistence;

import com.pawcycle.backend.catalog.engagement.domain.ReviewEntity;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<ReviewEntity, Long> {
  List<ReviewEntity> findTop30ByProductIdAndVisibleTrueOrderByCreatedAtDescIdDesc(Long productId);

  long countByProductIdAndVisibleTrue(Long productId);

  List<ReviewEntity> findByProductIdAndVisibleTrueOrderByIdAsc(Long productId);

  List<ReviewEntity> findByProductIdAndVisibleTrueOrderByCreatedAtDescIdDesc(
      Long productId, Pageable pageable);

  List<ReviewEntity> findByProductIdAndMemberId(Long productId, Long memberId);

  long countByProductId(Long productId);

  List<ReviewEntity> findByProductIdOrderByCreatedAtDescIdDesc(Long productId, Pageable pageable);

  List<ReviewEntity> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from ReviewEntity r where r.id = :reviewId")
  Optional<ReviewEntity> findByIdForUpdate(@Param("reviewId") Long reviewId);

  @Query("select avg(r.rating) from ReviewEntity r where r.productId = :productId and r.visible = true")
  Double averageVisibleRating(@Param("productId") Long productId);
}
