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
class CartItemId implements Serializable {
  @Column(name = "cart_id")
  Long cartId;

  @Column(name = "sku_id")
  Long skuId;

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
