package com.pawcycle.backend.catalog.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

@Entity
@Table(name = "product_detail_sections")
@Getter
public class ProductDetailSectionEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String body;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Column(nullable = false)
  private boolean visible;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected ProductDetailSectionEntity() {}

  public ProductDetailSectionEntity(
      long productId,
      String title,
      String body,
      int displayOrder,
      boolean visible,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {
    this.productId = productId;
    this.title = title;
    this.body = body;
    this.displayOrder = displayOrder;
    this.visible = visible;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public void update(String title, String body, int displayOrder, boolean visible, LocalDateTime updatedAt) {
    this.title = title;
    this.body = body;
    this.displayOrder = displayOrder;
    this.visible = visible;
    this.updatedAt = updatedAt;
  }
}
