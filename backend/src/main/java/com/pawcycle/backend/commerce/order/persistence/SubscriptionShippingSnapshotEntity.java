package com.pawcycle.backend.commerce.order.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * JPA mapping for a commerce persistence record.
 */

@Entity
@Table(name = "subscription_shipping_snapshots")
public class SubscriptionShippingSnapshotEntity {
  @Id
  @Column(name = "subscription_id")
  Long subscriptionId;

  @Column(name = "recipient_name", nullable = false, length = 100)
  String recipientName;

  @Column(name = "updated_at", nullable = false)
  LocalDateTime updatedAt;
  @Column(name = "recipient_phone", nullable = false) String recipientPhone;
  @Column(name = "postal_code", nullable = false) String postalCode;
  @Column(name = "address_line1", nullable = false) String addressLine1;
  @Column(name = "address_line2") String addressLine2;

  protected SubscriptionShippingSnapshotEntity() {}

  public SubscriptionShippingSnapshotEntity(long subscriptionId, String name, String phone,
      String postalCode, String line1, String line2, LocalDateTime updatedAt) {
    this.subscriptionId = subscriptionId;
    this.recipientName = name;
    this.recipientPhone = phone;
    this.postalCode = postalCode;
    this.addressLine1 = line1;
    this.addressLine2 = line2;
    this.updatedAt = updatedAt;
  }
}
