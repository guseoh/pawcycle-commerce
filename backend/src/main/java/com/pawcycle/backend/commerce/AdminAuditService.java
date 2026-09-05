package com.pawcycle.backend.commerce;

import com.pawcycle.backend.commerce.audit.api.AdminAuditResponse;
import com.pawcycle.backend.commerce.audit.persistence.AdminAuditPersistenceAdapter;
import com.pawcycle.backend.commerce.audit.persistence.AdminAuditView;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application boundary for the append-only admin audit feature. */
@Service
public class AdminAuditService {
  private final AdminAuditPersistenceAdapter audits;

  public AdminAuditService(AdminAuditPersistenceAdapter audits) {
    this.audits = audits;
  }

  @Transactional
  public void append(long adminId, String action, String targetType, long targetId) {
    audits.append(adminId, action, targetType, targetId);
  }

  @Transactional(readOnly = true)
  public List<AdminAuditResponse> list() {
    return audits.findAll().stream().map(AdminAuditService::response).toList();
  }

  private static AdminAuditResponse response(AdminAuditView view) {
    return new AdminAuditResponse(
        view.auditLogId(),
        view.adminId(),
        view.action(),
        view.targetType(),
        view.targetId(),
        view.createdAt());
  }
}
