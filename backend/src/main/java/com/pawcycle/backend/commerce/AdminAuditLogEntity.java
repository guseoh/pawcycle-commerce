package com.pawcycle.backend.commerce;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * JPA mapping for a commerce persistence record.
 */

@Entity
@Table(name = "admin_audit_logs")
@Getter
public class AdminAuditLogEntity {
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

  protected AdminAuditLogEntity() {}

  public AdminAuditLogEntity(
      long adminId, String action, String targetType, long targetId, LocalDateTime createdAt) {
    this.adminId = adminId;
    this.action = action;
    this.targetType = targetType;
    this.targetId = targetId;
    this.safeDetailJson = "{}";
    this.createdAt = createdAt;
  }
}
