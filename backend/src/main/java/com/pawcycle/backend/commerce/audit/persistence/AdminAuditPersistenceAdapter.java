package com.pawcycle.backend.commerce.audit.persistence;

import com.pawcycle.backend.commerce.AdminAuditLogEntity;
import com.pawcycle.backend.commerce.AdminAuditLogRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AdminAuditPersistenceAdapter {
  private final AdminAuditLogRepository audits;
  private final Clock clock;

  public AdminAuditPersistenceAdapter(AdminAuditLogRepository audits, Clock clock) {
    this.audits = audits;
    this.clock = clock;
  }

  @Transactional
  public void append(long adminId, String action, String targetType, long targetId) {
    audits.saveAndFlush(
        new AdminAuditLogEntity(
            adminId,
            action,
            targetType,
            targetId,
            LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault())));
  }

  @Transactional(readOnly = true)
  public List<AdminAuditView> findAll() {
    return audits.findAllByOrderByIdDesc().stream()
        .map(
            audit ->
                new AdminAuditView(
                    audit.getId(),
                    audit.getAdminId(),
                    audit.getAction(),
                    audit.getTargetType(),
                    audit.getTargetId(),
                    Timestamp.valueOf(audit.getCreatedAt())))
        .toList();
  }
}
