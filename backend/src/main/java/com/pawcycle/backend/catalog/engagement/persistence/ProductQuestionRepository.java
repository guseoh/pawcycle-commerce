package com.pawcycle.backend.catalog.engagement.persistence;

import com.pawcycle.backend.catalog.engagement.domain.ProductQuestionEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductQuestionRepository extends JpaRepository<ProductQuestionEntity, Long> {
  long countByProductIdAndVisibleTrue(long productId);

  List<ProductQuestionEntity> findByProductIdAndVisibleTrueOrderByCreatedAtDescIdDesc(
      long productId, Pageable pageable);

  long countByProductId(long productId);

  List<ProductQuestionEntity> findByProductIdOrderByCreatedAtDescIdDesc(
      long productId, Pageable pageable);

  List<ProductQuestionEntity> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select q from ProductQuestionEntity q where q.id = :questionId")
  Optional<ProductQuestionEntity> findByIdForUpdate(@Param("questionId") Long questionId);

  Optional<ProductQuestionEntity> findByIdAndProductId(Long id, Long productId);
}
