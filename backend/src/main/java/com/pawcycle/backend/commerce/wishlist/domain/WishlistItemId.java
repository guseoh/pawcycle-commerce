package com.pawcycle.backend.commerce.wishlist.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
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
