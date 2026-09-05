package com.pawcycle.backend.subscription.persistence;

import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SubscriptionMetricsQueryRepository {
  private final JdbcTemplate jdbc;

  public SubscriptionMetricsQueryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public long countCompletedRows(boolean creation) {
    return count("SELECT COUNT(*) FROM " + table(creation) + " WHERE completed_at IS NOT NULL");
  }

  public long countCleanupCandidates(boolean creation, LocalDateTime cutoff) {
    Long value =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM " + table(creation) + " WHERE completed_at < ?",
            Long.class,
            cutoff);
    return value == null ? 0L : value;
  }

  private long count(String sql) {
    Long value = jdbc.queryForObject(sql, Long.class);
    return value == null ? 0L : value;
  }

  private static String table(boolean creation) {
    return creation
        ? "subscription_creation_idempotency_results"
        : "subscription_command_idempotency_results";
  }
}
