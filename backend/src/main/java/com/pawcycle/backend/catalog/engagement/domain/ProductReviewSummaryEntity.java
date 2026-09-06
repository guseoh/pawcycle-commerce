package com.pawcycle.backend.catalog.engagement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_review_summaries")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ProductReviewSummaryEntity {
  @Id
  @Column(name = "product_id")
  private Long productId;

  @Column(name = "source_fingerprint", nullable = false, length = 64)
  private String sourceFingerprint;

  @Column(nullable = false, length = 500)
  private String summary;

  @Column(name = "generated_at", nullable = false)
  private LocalDateTime generatedAt;

  public ProductReviewSummaryEntity(
      Long productId, String sourceFingerprint, String summary, LocalDateTime generatedAt) {
    this.productId = productId;
    this.sourceFingerprint = sourceFingerprint;
    this.summary = summary;
    this.generatedAt = generatedAt;
  }

  public void update(String sourceFingerprint, String summary, LocalDateTime generatedAt) {
    this.sourceFingerprint = sourceFingerprint;
    this.summary = summary;
    this.generatedAt = generatedAt;
  }
}
