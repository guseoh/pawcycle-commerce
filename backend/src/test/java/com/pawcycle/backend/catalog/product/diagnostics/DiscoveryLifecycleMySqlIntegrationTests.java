package com.pawcycle.backend.catalog.product.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawcycle.backend.catalog.product.application.ProductQueryService;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.ConfigurableTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

/** No test-managed transaction: observe the real proxy begin and final connection return. */
@SpringBootTest(properties = "pawcycle.catalog.product.discovery.diagnostics.enabled=true")
@ActiveProfiles("test")
@Import(DiscoveryLifecycleMySqlIntegrationTests.ProbeConfiguration.class)
class DiscoveryLifecycleMySqlIntegrationTests {
  @Autowired DiscoveryLifecycleDiagnostics diagnostics;
  @Autowired MeterRegistry registry;
  @Autowired ProductQueryService service;

  @Autowired Probe probe;
  @Autowired DataSource dataSource;
  @Autowired EntityManagerFactory entityManagerFactory;
  @Autowired PlatformTransactionManager manager;

  @Test void actualDiscoveryOwnsTransactionAndReturnsConnection() {
    int activeBefore = DiscoveryPoolCiEvidence.active(dataSource);
    double before = calls("matched");
    double matchedPairs = pairs("matched");
    double unmatchedPairs = pairs("unmatched");
    double unmatchedCalls = calls("unmatched");
    assertThat(service.findProducts().items()).isNotNull();
    assertThat(calls("matched")).isEqualTo(before + 1);
    assertThat(pairs("matched")).isGreaterThan(matchedPairs);
    assertThat(pairs("unmatched")).isEqualTo(unmatchedPairs);
    assertThat(calls("unmatched")).isEqualTo(unmatchedCalls);
    assertThat(DiscoveryLifecycleDiagnostics.currentScope()).isNull();
    assertThat(dataSource).isInstanceOf(HikariDataSource.class);
    DiscoveryPoolCiEvidence.assertRequestThreadClean(entityManagerFactory, dataSource, "actualDiscovery");
    DiscoveryPoolCiEvidence.observeSharedPool(dataSource, activeBefore, "actualDiscovery");
    assertThat(((ConfigurableTransactionManager) manager).getTransactionExecutionListeners()).contains(diagnostics.transactionListener());
    assertThat(registry.get("pawcycle.catalog.product.discovery.phase").tag("phase", "repository-total").timer().count()).isPositive();
    for (String phase : java.util.List.of("transaction-begin", "transaction-commit", "transaction-cleanup",
        "connection-acquire", "connection-release", "connection-lease-total", "repository-body-paired")) {
      assertThat(registry.get(DiscoveryLifecycleDiagnostics.METRIC).tag("phase", phase).timer().count()).isPositive();
    }
  }

  @Test void realCountAndListSqlFailuresRollbackAndReturnConnection() {
    for (int failurePoint : new int[] {1, 2}) {
      int activeBefore = DiscoveryPoolCiEvidence.active(dataSource);
      double before = calls("matched");
      double matchedPairs = pairs("matched");
      double unmatchedPairs = pairs("unmatched");
      double unmatchedCalls = calls("unmatched");
      long rollbacks = registry.get(DiscoveryLifecycleDiagnostics.METRIC).tag("phase", "transaction-rollback").timer().count();
      assertThatThrownBy(() -> {
        try (var scope = diagnostics.openScope()) { probe.read(failurePoint); }
      }).isInstanceOf(RuntimeException.class);
      assertThat(calls("matched")).isEqualTo(before + 1);
      assertThat(pairs("matched")).isGreaterThan(matchedPairs);
      assertThat(pairs("unmatched")).isEqualTo(unmatchedPairs);
      assertThat(calls("unmatched")).isEqualTo(unmatchedCalls);
      assertThat(registry.get(DiscoveryLifecycleDiagnostics.METRIC).tag("phase", "transaction-rollback").timer().count()).isEqualTo(rollbacks + 1);
      assertThat(DiscoveryLifecycleDiagnostics.currentScope()).isNull();
      DiscoveryPoolCiEvidence.assertRequestThreadClean(entityManagerFactory, dataSource, "failureRollback");
      DiscoveryPoolCiEvidence.observeSharedPool(dataSource, activeBefore, "failureRollback");
    }
  }

  @Test void actualJdbcLeaseHandleClosesAfterCommitAndRollback() throws Exception {
    assertThat(dataSource).isInstanceOf(HikariDataSource.class);
    for (boolean fail : new boolean[] {false, true}) {
      AtomicReference<Connection> captured = new AtomicReference<>();
      double matchedBefore = calls("matched");
      double unmatchedBefore = calls("unmatched");
      double matchedPairsBefore = pairs("matched");
      double unmatchedPairsBefore = pairs("unmatched");
      try (var scope = diagnostics.openScope()) {
        if (fail) {
          assertThatThrownBy(() -> probe.readWithLeaseCapture(captured, true))
              .isInstanceOf(RuntimeException.class);
        } else {
          probe.readWithLeaseCapture(captured, false);
        }
      }
      Connection lease = captured.get();
      assertThat(lease).as("Hibernate exposed the borrowed JDBC connection").isNotNull();
      // A Hikari close returns this logical lease to the pool; it need not close the physical socket.
      assertThat(lease.isClosed()).as("borrowed JDBC handle returned after " +
          (fail ? "rollback" : "commit")).isTrue();
      assertThat(calls("matched")).isEqualTo(matchedBefore + 1);
      assertThat(calls("unmatched")).isEqualTo(unmatchedBefore);
      assertThat(pairs("matched")).isGreaterThan(matchedPairsBefore);
      assertThat(pairs("unmatched")).isEqualTo(unmatchedPairsBefore);
      assertThat(DiscoveryLifecycleDiagnostics.currentScope()).isNull();
      DiscoveryPoolCiEvidence.assertRequestThreadClean(
          entityManagerFactory, dataSource, fail ? "leaseRollback" : "leaseCommit");
    }
  }

  @Test void realConcurrentTransactionsDoNotMixReusedWorkerScopes() throws Exception {
    int activeBefore = DiscoveryPoolCiEvidence.active(dataSource);
    double before = calls("matched");
    double matchedPairs = pairs("matched");
    double unmatchedPairs = pairs("unmatched");
    double unmatchedCalls = calls("unmatched");
    try (var executor = Executors.newFixedThreadPool(2)) {
      var futures = executor.invokeAll(java.util.stream.IntStream.range(0, 6)
          .<java.util.concurrent.Callable<Boolean>>mapToObj(i -> () -> {
            try (var scope = diagnostics.openScope()) { probe.read(0); }
            DiscoveryPoolCiEvidence.assertRequestThreadClean(entityManagerFactory, dataSource, "concurrentWorker");
            return DiscoveryLifecycleDiagnostics.currentScope() == null;
          }).toList());
      for (var future : futures) assertThat(future.get()).isTrue();
    }
    assertThat(calls("matched")).isEqualTo(before + 6);
    assertThat(pairs("matched")).isGreaterThanOrEqualTo(matchedPairs + 6);
    assertThat(pairs("unmatched")).isEqualTo(unmatchedPairs);
    assertThat(calls("unmatched")).isEqualTo(unmatchedCalls);
    DiscoveryPoolCiEvidence.assertRequestThreadClean(entityManagerFactory, dataSource, "concurrentWorkers");
    DiscoveryPoolCiEvidence.observeSharedPool(dataSource, activeBefore, "concurrentWorkers");
  }

  @Test void unrelatedBorrowerKeepsPoolActiveAfterCompletedDiscovery() throws Exception {
    // Deterministic counterexample: a pool-wide active count is not a request-local lease.
    assertThat(dataSource).isInstanceOf(HikariDataSource.class);
    var pool = ((HikariDataSource) dataSource).getHikariPoolMXBean();
    assertThat(pool).isNotNull();
    CountDownLatch borrowed = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<Void> borrower = executor.submit(() -> {
      try (var ignored = dataSource.getConnection()) {
        borrowed.countDown();
        if (!release.await(15, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Controlled Hikari borrower was not released in time");
        }
      }
      return null;
    });
    try {
      assertThat(borrowed.await(5, TimeUnit.SECONDS)).as("borrower acquired connection").isTrue();
      assertThat(pool.getActiveConnections()).as("controlled borrower holds a lease").isPositive();
      double matchedBefore = calls("matched");
      double unmatchedBefore = calls("unmatched");
      double matchedPairs = pairs("matched");
      double unmatchedPairs = pairs("unmatched");
      assertThat(service.findProducts().items()).isNotNull();
      assertThat(calls("matched")).isEqualTo(matchedBefore + 1);
      assertThat(calls("unmatched")).isEqualTo(unmatchedBefore);
      assertThat(pairs("matched")).isGreaterThan(matchedPairs);
      assertThat(pairs("unmatched")).isEqualTo(unmatchedPairs);
      assertThat(DiscoveryLifecycleDiagnostics.currentScope()).isNull();
      DiscoveryPoolCiEvidence.assertRequestThreadClean(entityManagerFactory, dataSource, "borrowerControl");
      // A different legitimate borrower remains active after discovery has returned.
      assertThat(pool.getActiveConnections()).as("unrelated borrower is still active").isPositive();
    } finally {
      release.countDown();
      try {
        borrower.get(5, TimeUnit.SECONDS);
      } finally {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS))
            .as("controlled borrower worker stopped").isTrue();
      }
    }
  }

  private double calls(String result) {
    return registry.get(DiscoveryLifecycleDiagnostics.METRIC + ".calls").tag("result", result).counter().count();
  }

  private double pairs(String result) {
    return registry.get(DiscoveryLifecycleDiagnostics.METRIC + ".pairs").tag("result", result).counter().count();
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ProbeConfiguration {
    @Bean Probe lifecycleProbe() { return new Probe(); }
  }
  static class Probe {
    @PersistenceContext EntityManager entityManager;
    @Transactional(readOnly = true)
    public void readWithLeaseCapture(AtomicReference<Connection> captured, boolean fail) {
      DiscoveryLifecycleDiagnostics.repositoryEnter();
      try {
        entityManager.unwrap(Session.class).doWork(connection -> {
          assertThat(connection.isClosed()).as("connection is open inside the transaction").isFalse();
          captured.set(connection);
        });
        entityManager.createNativeQuery(fail
            ? "SELECT lifecycle_missing_column FROM products" : "SELECT 1").getSingleResult();
      } finally {
        DiscoveryLifecycleDiagnostics.repositoryExit();
      }
    }

    @Transactional(readOnly = true)
    public void read(int failurePoint) {
      DiscoveryLifecycleDiagnostics.repositoryEnter();
      try {
        entityManager.createNativeQuery(failurePoint == 1 ? "SELECT lifecycle_missing_column FROM products" : "SELECT 1").getSingleResult();
        entityManager.createNativeQuery(failurePoint == 2 ? "SELECT lifecycle_missing_column FROM products" : "SELECT 2").getResultList();
      } finally { DiscoveryLifecycleDiagnostics.repositoryExit(); }
    }
  }
}
