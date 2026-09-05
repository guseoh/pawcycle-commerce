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

@Embeddable
public class WishlistItemId implements Serializable {
  @Column(name = "member_id")
  private Long memberId;

  @Column(name = "product_id")
  private Long productId;

  protected WishlistItemId() {}

  public WishlistItemId(long memberId, long productId) {
    this.memberId = memberId;
    this.productId = productId;
  }

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
