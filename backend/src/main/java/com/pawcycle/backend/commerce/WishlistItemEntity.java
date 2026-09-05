package com.pawcycle.backend.commerce;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "wishlist_items")
public class WishlistItemEntity {
  @EmbeddedId private WishlistItemId id;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected WishlistItemEntity() {}

  public WishlistItemEntity(WishlistItemId id, LocalDateTime createdAt) {
    this.id = id;
    this.createdAt = createdAt;
  }
}
