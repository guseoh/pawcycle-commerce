package com.pawcycle.backend.support;

import java.sql.SQLException;
import org.junit.jupiter.api.Assumptions;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Privileged physical observation is supplemental to the ordinary database regressions. */
public final class MysqlLockObservation {
  private MysqlLockObservation() {}

  public static void requireAccess(JdbcTemplate jdbc) {
    try {
      jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_locks", Long.class);
      jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits", Long.class);
      jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.threads", Long.class);
      jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.INNODB_METRICS", Long.class);
    } catch (DataAccessException failure) {
      Throwable cause = failure;
      while (!(cause instanceof SQLException) && cause.getCause() != null) cause = cause.getCause();
      if (cause instanceof SQLException sql && (sql.getErrorCode() == 1142
          || sql.getErrorCode() == 1143 || sql.getErrorCode() == 1227)) {
        Assumptions.abort("MySQL account lacks physical lock observation privileges (error " + sql.getErrorCode() + ")");
      }
      throw failure;
    }
  }
}
