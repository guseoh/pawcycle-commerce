package com.pawcycle.backend.commerce.audit.persistence;

import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class AdminAuditPersistenceAdapter {
  private final NativeQueryExecutor queries;
  private final Clock clock;

  public AdminAuditPersistenceAdapter(NativeQueryExecutor queries, Clock clock) {
    this.queries = queries;
    this.clock = clock;
  }

  public void append(long adminId, String action, String targetType, long targetId) {
    queries.update(
        "INSERT INTO admin_audit_logs(admin_id,action,target_type,target_id,safe_detail_json,created_at) VALUES (?,?,?,?,JSON_OBJECT(),?)",
        adminId,
        action,
        targetType,
        targetId,
        Timestamp.from(clock.instant()));
  }

  public List<AdminAuditView> findAll() {
    return queries.query(
        "SELECT id AS auditLogId,admin_id AS adminId,action,target_type AS targetType,target_id AS targetId,created_at AS createdAt FROM admin_audit_logs ORDER BY id DESC",
        (rs, rowNumber) ->
            new AdminAuditView(
                rs.getLong("auditLogId"),
                rs.getLong("adminId"),
                rs.getString("action"),
                rs.getString("targetType"),
                rs.getLong("targetId"),
                rs.getTimestamp("createdAt")));
  }
}
