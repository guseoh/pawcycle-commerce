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

/**
 * JPA mapping for a commerce persistence record.
 */

@Embeddable
class WishlistItemId implements Serializable {
  @Column(name = "member_id")
  Long memberId;

  @Column(name = "product_id")
  Long productId;

  @Override
  public boolean equals(Object other) {
    return other instanceof WishlistItemId id
        && Objects.equals(memberId, id.memberId)
        && Objects.equals(productId, id.productId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(memberId, productId);
  }
}
