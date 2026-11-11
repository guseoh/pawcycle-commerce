package com.pawcycle.backend.commerce.wishlist.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.LocalDateTimeJdbcType;

@Entity
@Table(name = "wishlist_items")
public class WishlistItemEntity {
  @EmbeddedId private WishlistItemId id;

  @JdbcType(LocalDateTimeJdbcType.class)
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected WishlistItemEntity() {}

  public WishlistItemEntity(WishlistItemId id, LocalDateTime createdAt) {
    this.id = id;
    this.createdAt = createdAt;
  }
}
