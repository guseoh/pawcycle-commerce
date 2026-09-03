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

@Entity
@Table(name = "order_items")
class CommerceOrderItemEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "order_id", nullable = false)
  Long orderId;

  @Column(name = "sku_id", nullable = false)
  Long skuId;

  @Column(nullable = false)
  int quantity;

  @Column(name = "line_amount", precision = 18, scale = 2)
  BigDecimal lineAmount;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", insertable = false, updatable = false)
  CommerceOrderEntity order;
}
