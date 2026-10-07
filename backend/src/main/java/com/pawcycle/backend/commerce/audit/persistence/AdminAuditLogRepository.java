package com.pawcycle.backend.commerce.audit.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLogEntity, Long> {
  List<AdminAuditLogEntity> findAllByOrderByIdDesc();
}
