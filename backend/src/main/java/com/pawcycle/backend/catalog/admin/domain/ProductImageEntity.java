package com.pawcycle.backend.catalog.admin.domain;

import com.pawcycle.backend.catalog.product.domain.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_images")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ProductImageEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @Column(name = "image_url", nullable = false, length = 2048)
  private String imageUrl;

  @Column(name = "alt_text", length = 500)
  private String altText;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Column(name = "image_type", nullable = false, length = 20)
  private String imageType;

  public ProductImageEntity(
      Product product, String imageUrl, String altText, int displayOrder, String imageType) {
    this.product = product;
    this.imageUrl = imageUrl;
    this.altText = altText;
    this.displayOrder = displayOrder;
    this.imageType = imageType;
  }

  public void update(String imageUrl, String altText, int displayOrder, String imageType) {
    this.imageUrl = imageUrl;
    this.altText = altText;
    this.displayOrder = displayOrder;
    this.imageType = imageType;
  }
}
