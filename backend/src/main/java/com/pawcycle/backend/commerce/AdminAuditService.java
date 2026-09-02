package com.pawcycle.backend.commerce;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Append-only audit writer. Callers pass only safe, non-secret JSON literals. */
@Service
public class AdminAuditService {
  private final JdbcTemplate jdbc;

  public AdminAuditService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public void append(long adminId, String action, String targetType, long targetId) {
    jdbc.update(
        "INSERT INTO"
            + " admin_audit_logs(admin_id,action,target_type,target_id,safe_detail_json,created_at)"
            + " VALUES (?,?,?,?,JSON_OBJECT(),?)",
        adminId,
        action,
        targetType,
        targetId,
        Timestamp.from(Instant.now()));
  }

  public List<Map<String, Object>> list() {
    return jdbc.queryForList(
        "SELECT id AS auditLogId,admin_id AS adminId,action,target_type AS targetType,target_id AS"
            + " targetId,created_at AS createdAt FROM admin_audit_logs ORDER BY id DESC");
  }
}
