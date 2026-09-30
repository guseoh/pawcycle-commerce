package com.pawcycle.backend.catalog.product.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawcycle.backend.catalog.product.application.ProductQueryService;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
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
  @Autowired PlatformTransactionManager manager;

  @Test void actualDiscoveryOwnsTransactionAndReturnsConnection() {
    double before = calls("matched");
    assertThat(service.findProducts().items()).isNotNull();
    assertThat(calls("matched")).isEqualTo(before + 1);
    assertThat(DiscoveryLifecycleDiagnostics.currentScope()).isNull();
    assertThat(dataSource).isInstanceOf(HikariDataSource.class);
    assertThat(((HikariDataSource) dataSource).getHikariPoolMXBean().getActiveConnections()).isZero();
    assertThat(((ConfigurableTransactionManager) manager).getTransactionExecutionListeners()).contains(diagnostics.transactionListener());
    assertThat(registry.get("pawcycle.catalog.product.discovery.phase").tag("phase", "repository-total").timer().count()).isPositive();
    for (String phase : java.util.List.of("transaction-begin", "transaction-commit", "transaction-cleanup",
        "connection-acquire", "connection-release", "connection-lease-total", "repository-body-paired")) {
      assertThat(registry.get(DiscoveryLifecycleDiagnostics.METRIC).tag("phase", phase).timer().count()).isPositive();
    }
  }

  @Test void realCountAndListSqlFailuresRollbackAndReturnConnection() {
    for (int failurePoint : new int[] {1, 2}) {
      double before = calls("matched");
      long rollbacks = registry.get(DiscoveryLifecycleDiagnostics.METRIC).tag("phase", "transaction-rollback").timer().count();
      assertThatThrownBy(() -> {
        try (var scope = diagnostics.openScope()) { probe.read(failurePoint); }
      }).isInstanceOf(RuntimeException.class);
      assertThat(calls("matched")).isEqualTo(before + 1);
      assertThat(registry.get(DiscoveryLifecycleDiagnostics.METRIC).tag("phase", "transaction-rollback").timer().count()).isEqualTo(rollbacks + 1);
      assertThat(DiscoveryLifecycleDiagnostics.currentScope()).isNull();
      assertThat(((HikariDataSource) dataSource).getHikariPoolMXBean().getActiveConnections()).isZero();
    }
  }

  @Test void realConcurrentTransactionsDoNotMixReusedWorkerScopes() throws Exception {
    double before = calls("matched");
    try (var executor = Executors.newFixedThreadPool(2)) {
      var futures = executor.invokeAll(java.util.stream.IntStream.range(0, 6)
          .<java.util.concurrent.Callable<Boolean>>mapToObj(i -> () -> {
            try (var scope = diagnostics.openScope()) { probe.read(0); }
            return DiscoveryLifecycleDiagnostics.currentScope() == null;
          }).toList());
      for (var future : futures) assertThat(future.get()).isTrue();
    }
    assertThat(calls("matched")).isEqualTo(before + 6);
    assertThat(((HikariDataSource) dataSource).getHikariPoolMXBean().getActiveConnections()).isZero();
  }

  private double calls(String result) {
    return registry.get(DiscoveryLifecycleDiagnostics.METRIC + ".calls").tag("result", result).counter().count();
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ProbeConfiguration {
    @Bean Probe lifecycleProbe() { return new Probe(); }
  }
  static class Probe {
    @PersistenceContext EntityManager entityManager;
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
