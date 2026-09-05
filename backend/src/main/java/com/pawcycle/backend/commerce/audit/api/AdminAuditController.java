package com.pawcycle.backend.commerce.audit.api;

import com.pawcycle.backend.commerce.AdminAuditService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditController {
  private final AdminAuditService audits;

  public AdminAuditController(AdminAuditService audits) {
    this.audits = audits;
  }

  @GetMapping
  public List<AdminAuditResponse> list() {
    return audits.list();
  }
}
