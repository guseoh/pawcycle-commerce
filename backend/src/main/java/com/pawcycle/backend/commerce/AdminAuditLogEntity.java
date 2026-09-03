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
@Table(name = "admin_audit_logs")
class AdminAuditLogEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "admin_id", nullable = false)
  Long adminId;

  @Column(nullable = false, length = 80)
  String action;

  @Column(name = "target_type", nullable = false, length = 40)
  String targetType;

  @Column(name = "target_id", nullable = false)
  Long targetId;

  @Column(name = "safe_detail_json", nullable = false, columnDefinition = "json")
  String safeDetailJson;

  @Column(name = "created_at", nullable = false)
  LocalDateTime createdAt;
}