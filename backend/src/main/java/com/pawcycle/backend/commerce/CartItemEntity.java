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
@Table(name = "cart_items")
public class CartItemEntity {
  @EmbeddedId private CartItemId id;

  @Column(nullable = false)
  private int quantity;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "cart_id", insertable = false, updatable = false)
  private CartEntity cart;

  protected CartItemEntity() {}

  public CartItemEntity(CartItemId id, int quantity) {
    this.id = id;
    this.quantity = quantity;
  }

  public int getQuantity() {
    return quantity;
  }

  public void increase(int quantity) {
    this.quantity += quantity;
  }

  public void updateQuantity(int quantity) {
    this.quantity = quantity;
  }
}
