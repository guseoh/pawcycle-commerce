package com.pawcycle.backend.subscription.persistence;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Compatibility at the subscription JDBC-to-JPA write boundary only. */
final class SubscriptionJdbcTime {
  private SubscriptionJdbcTime() {}

  /**
   * Existing shared entities bind Timestamp with Hibernate's UTC Calendar, while subscription SQL
   * bound LocalDateTime directly. Encode the same DATETIME wall clock for that binder. Inserts are
   * flushed and detached immediately; this value must not escape as an application timestamp.
   */
  static LocalDateTime forUtcCalendar(LocalDateTime jdbcWallClock) {
    return LocalDateTime.ofInstant(jdbcWallClock.toInstant(ZoneOffset.UTC), ZoneId.systemDefault());
  }
}
