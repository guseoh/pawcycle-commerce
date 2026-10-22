package com.pawcycle.timeexperiment;

import com.pawcycle.backend.commerce.wishlist.domain.WishlistItemId;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Frozen pre-C2 mapping, scanned only in the legacy compatibility diagnostic context. */
@Entity(name = "WishlistLegacyBindingProbe")
@Table(name = "wishlist_items")
public class WishlistLegacyBindingProbe {
  @EmbeddedId private WishlistItemId id;
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected WishlistLegacyBindingProbe() {}

  public WishlistLegacyBindingProbe(WishlistItemId id, LocalDateTime createdAt) {
    this.id = id;
    this.createdAt = createdAt;
  }
}
