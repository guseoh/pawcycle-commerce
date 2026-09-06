package com.pawcycle.backend.commerce;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class CartItemId implements Serializable {
  @Column(name = "cart_id")
  private Long cartId;

  @Column(name = "sku_id")
  private Long skuId;

  protected CartItemId() {}

  public CartItemId(long cartId, long skuId) {
    this.cartId = cartId;
    this.skuId = skuId;
  }

  public long cartId() {
    return cartId;
  }

  public long skuId() {
    return skuId;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof CartItemId id
        && Objects.equals(cartId, id.cartId)
        && Objects.equals(skuId, id.skuId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(cartId, skuId);
  }
}
