package com.pawcycle.backend.commerce.notification.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * JPA mapping for a commerce persistence record.
 */

@Entity
@Table(name = "notifications")
class NotificationEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "member_id", nullable = false)
  Long memberId;

  @Column(nullable = false, length = 40)
  String type;

  @Column(name = "reference_type", nullable = false, length = 40)
  String referenceType;

  @Column(name = "reference_id", nullable = false)
  Long referenceId;

  @Column(name = "created_at", nullable = false)
  LocalDateTime createdAt;
}
