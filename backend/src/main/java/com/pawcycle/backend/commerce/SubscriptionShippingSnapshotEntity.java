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
@Table(name = "subscription_shipping_snapshots")
class SubscriptionShippingSnapshotEntity {
  @Id
  @Column(name = "subscription_id")
  Long subscriptionId;

  @Column(name = "recipient_name", nullable = false, length = 100)
  String recipientName;

  @Column(name = "updated_at", nullable = false)
  LocalDateTime updatedAt;
}
