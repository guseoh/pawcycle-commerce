package com.pawcycle.backend.commerce.metrics.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CommerceMetricsQueryRepository {
  private final JdbcTemplate jdbc;

  public CommerceMetricsQueryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public long countPendingOperations() {
    Long value =
        jdbc.queryForObject(
            "SELECT (SELECT COUNT(*) FROM order_returns WHERE status='REQUESTED')+(SELECT COUNT(*)"
                + " FROM refunds WHERE status IN ('READY','FAILED','UNKNOWN'))+(SELECT COUNT(*)"
                + " FROM payments WHERE status IN ('UNKNOWN'))",
            Long.class);
    return value == null ? 0L : value;
  }
}
