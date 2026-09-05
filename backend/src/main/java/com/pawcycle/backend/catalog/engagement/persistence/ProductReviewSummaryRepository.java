package com.pawcycle.backend.catalog.engagement.persistence;

import com.pawcycle.backend.catalog.engagement.domain.ProductReviewSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductReviewSummaryRepository
    extends JpaRepository<ProductReviewSummaryEntity, Long> {}
