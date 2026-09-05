package com.pawcycle.backend.catalog.engagement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

@Entity
@Table(name = "reviews")
@Getter
public class ReviewEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(name = "member_id", nullable = false)
  private Long memberId;

  @Column(nullable = false)
  private int rating;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(nullable = false)
  private boolean visible;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected ReviewEntity() {}

  public ReviewEntity(
      long productId,
      long memberId,
      int rating,
      String content,
      boolean visible,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {
    this.productId = productId;
    this.memberId = memberId;
    this.rating = rating;
    this.content = content;
    this.visible = visible;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public void update(int rating, String content, LocalDateTime updatedAt) {
    this.rating = rating;
    this.content = content;
    this.updatedAt = updatedAt;
  }

  public void updateVisibility(boolean visible, LocalDateTime updatedAt) {
    this.visible = visible;
    this.updatedAt = updatedAt;
  }
}
