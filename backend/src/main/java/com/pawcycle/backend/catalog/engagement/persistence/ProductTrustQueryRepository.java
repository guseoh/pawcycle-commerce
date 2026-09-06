package com.pawcycle.backend.catalog.engagement.persistence;

import com.pawcycle.backend.catalog.engagement.domain.ReviewEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ProductTrustQueryRepository extends Repository<ReviewEntity, Long> {
  @Query(
      "select new com.pawcycle.backend.catalog.engagement.persistence.ProductTrustRow("
          + "avg(review.rating), count(review))"
          + " from ReviewEntity review"
          + " where review.productId = :productId and review.visible = true")
  Optional<ProductTrustRow> findVisibleSummary(@Param("productId") long productId);
}
