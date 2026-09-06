package com.pawcycle.backend.catalog.engagement.persistence;

import com.pawcycle.backend.catalog.engagement.domain.ProductReviewSummaryEntity;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductReviewSummaryRepository
    extends JpaRepository<ProductReviewSummaryEntity, Long> {
  @Modifying
  @Query(
      value =
          """
          INSERT INTO product_review_summaries(product_id,source_fingerprint,summary,generated_at)
          VALUES (:productId,:fingerprint,:summary,:generatedAt)
          ON DUPLICATE KEY UPDATE
            source_fingerprint=VALUES(source_fingerprint),
            summary=VALUES(summary),
            generated_at=VALUES(generated_at)
          """,
      nativeQuery = true)
  int upsert(
      @Param("productId") long productId,
      @Param("fingerprint") String fingerprint,
      @Param("summary") String summary,
      @Param("generatedAt") LocalDateTime generatedAt);
}
