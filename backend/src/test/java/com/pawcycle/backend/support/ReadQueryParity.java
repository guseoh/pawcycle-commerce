package com.pawcycle.backend.support;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;

/** Same transaction/connection: compare results, server SELECT count and handler work, not JVM timing. */
public final class ReadQueryParity {
  private ReadQueryParity() {}

  public static <T> T compare(JdbcTemplate jdbc, Supplier<T> legacy, Supplier<T> typed) {
    Map<String, Long> before = counters(jdbc);
    T expected = legacy.get();
    Map<String, Long> middle = counters(jdbc);
    T actual = typed.get();
    Map<String, Long> after = counters(jdbc);
    assertThat(actual).isEqualTo(expected);
    long oldSelects = middle.get("Com_select") - before.get("Com_select");
    long newSelects = after.get("Com_select") - middle.get("Com_select");
    assertThat(newSelects).as("DB SELECT count").isEqualTo(oldSelects);
    // Handler counters measure logical DB work independent of cache warmth or timing noise.
    for (String counter : new String[] {"Select_full_join", "Select_scan", "Handler_read_rnd_next", "Handler_read_key"}) {
      long oldWork = middle.get(counter) - before.get(counter);
      long newWork = after.get(counter) - middle.get(counter);
      assertThat(newWork).as("%s: typed vs frozen JDBC", counter).isLessThanOrEqualTo(oldWork);
    }
    return actual;
  }

  private static Map<String, Long> counters(JdbcTemplate jdbc) {
    Map<String, Long> values = new java.util.HashMap<>();
    jdbc.query("SHOW SESSION STATUS WHERE Variable_name IN ('Com_select','Select_full_join',"
        + "'Select_scan','Handler_read_rnd_next','Handler_read_key')", (org.springframework.jdbc.core.RowCallbackHandler)
        rs -> values.put(rs.getString(1), rs.getLong(2)));
    return values;
  }
}
