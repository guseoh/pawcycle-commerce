package com.pawcycle.backend.catalog.product.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;
import javax.sql.DataSource;

/** Test-only diagnostics; retains the strict original zero-active assertion. */
final class DiscoveryPoolCiEvidence {
  private DiscoveryPoolCiEvidence() {}

  static int active(DataSource dataSource) {
    if (dataSource instanceof HikariDataSource hikari && hikari.getHikariPoolMXBean() != null) {
      return hikari.getHikariPoolMXBean().getActiveConnections();
    }
    return -1; // Pool not initialized; avoid borrowing a connection merely to sample it.
  }

  static void assertIdle(DataSource dataSource, int before, String boundary) {
    HikariDataSource hikari = (HikariDataSource) dataSource;
    var pool = hikari.getHikariPoolMXBean();
    int observed = pool.getActiveConnections();
    if (observed != 0) {
      // Capture workers as close to the first nonzero snapshot as possible.
      // Pool counts are aggregate and may change independently between these samples.
      int firstIdle = pool.getIdleConnections();
      int firstTotal = pool.getTotalConnections();
      int firstPending = pool.getThreadsAwaitingConnection();
      List<String> firstWorkers = dbWorkerStacks();
      // The first nonzero sample still fails, even if the later values reach zero.
      int[] later = new int[12];
      for (int i = 0; i < later.length; i++) {
        LockSupport.parkNanos(20_000_000L);
        later[i] = pool.getActiveConnections();
      }
      System.err.printf(
          "DISCOVERY_POOL_CI_EVIDENCE boundary=%s before=%d observed=%d firstIdle=%d firstTotal=%d firstPending=%d firstWorkers=%s later=%s finalIdle=%d finalTotal=%d finalPending=%d finalWorkers=%s%n",
          boundary, before, observed, firstIdle, firstTotal, firstPending, firstWorkers,
          Arrays.toString(later), pool.getIdleConnections(), pool.getTotalConnections(),
          pool.getThreadsAwaitingConnection(), dbWorkerStacks());
    }
    assertThat(observed).as("shared Hikari active connections after " + boundary).isZero();
  }

  private static List<String> dbWorkerStacks() {
    return Thread.getAllStackTraces().entrySet().stream()
        .filter(e -> {
          String name = e.getKey().getName();
          return name.contains("scheduling") || name.contains("idempotency-metrics")
              || name.contains("taskScheduler")
              || Arrays.stream(e.getValue()).anyMatch(frame ->
                  frame.getClassName().startsWith("com.pawcycle.backend.") &&
                  (frame.getClassName().contains("Metrics")
                      || frame.getClassName().contains("Expiration")));
        })
        .limit(12)
        .map(e -> e.getKey().getName() + "/" + e.getKey().getState() + ":" +
            Arrays.stream(e.getValue())
                .filter(f -> f.getClassName().startsWith("com.pawcycle.backend.")
                    || f.getClassName().startsWith("org.springframework.jdbc.")
                    || f.getClassName().startsWith("com.zaxxer.hikari.")
                    || f.getClassName().startsWith("com.mysql."))
                .limit(6)
                .map(f -> f.getClassName() + "." + f.getMethodName())
                .collect(Collectors.joining(">")))
        .toList();
  }
}
