package com.pawcycle.backend.catalog.product.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.SimpleTransactionStatus;

class DiscoveryLifecycleDiagnosticsTests {
  private final MockClock clock = new MockClock();
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
  private final DiscoveryLifecycleDiagnostics diagnostics = new DiscoveryLifecycleDiagnostics(registry, true);

  @Test void disabledDoesNotInstallScopeOrMeters() {
    var disabledRegistry = new SimpleMeterRegistry();
    var disabled = new DiscoveryLifecycleDiagnostics(disabledRegistry, false);
    assertThat(disabled.openScope()).isNull();
    assertThat(DiscoveryLifecycleDiagnostics.currentScope()).isNull();
    assertThat(disabledRegistry.getMeters()).isEmpty();
  }

  @Test void completeCallSeparatesAcquireFromHoldAndCompletionFromCleanup() {
    var tx = new SimpleTransactionStatus();
    try (var scope = diagnostics.openScope()) {
      diagnostics.beforeBegin(tx);
      var listener = new DiscoverySessionEventListener();
      listener.jdbcConnectionAcquisitionStart(); advance(2);
      listener.jdbcConnectionAcquisitionEnd(); advance(3);
      diagnostics.afterBegin(tx, null);
      DiscoveryLifecycleDiagnostics.repositoryEnter(); advance(7);
      DiscoveryLifecycleDiagnostics.repositoryExit();
      diagnostics.beforeCommit(tx); advance(4);
      diagnostics.afterCommit(tx, null); advance(5);
      listener.jdbcConnectionReleaseStart(); advance(6);
      listener.jdbcConnectionReleaseEnd();
    }
    assertMillis("connection-acquire", 2);
    assertMillis("pre-repository", 3);
    assertMillis("repository-with-connection", 7);
    assertMillis("post-repository-connection-hold", 9);
    assertMillis("connection-release", 6);
    assertMillis("connection-lease-total", 25);
    assertMillis("transaction-commit", 4);
    assertMillis("transaction-cleanup", 11);
    assertThat(coverage("calls", "matched")).isEqualTo(1);
    assertThat(DiscoveryLifecycleDiagnostics.currentScope()).isNull();
  }

  @Test void multiplePairsAndAcquisitionInsideBodyAreMeasuredByIntersection() {
    var tx = new SimpleTransactionStatus();
    try (var scope = diagnostics.openScope()) {
      diagnostics.beforeBegin(tx); diagnostics.afterBegin(tx, null);
      var listener = new DiscoverySessionEventListener();
      DiscoveryLifecycleDiagnostics.repositoryEnter(); advance(1);
      for (int i = 0; i < 2; i++) {
        listener.jdbcConnectionAcquisitionStart(); advance(2);
        listener.jdbcConnectionAcquisitionEnd(); advance(3);
        listener.jdbcConnectionReleaseStart(); advance(4);
        listener.jdbcConnectionReleaseEnd();
      }
      DiscoveryLifecycleDiagnostics.repositoryExit();
      diagnostics.beforeCommit(tx); diagnostics.afterCommit(tx, null);
    }
    assertThat(coverage("pairs", "matched")).isEqualTo(2);
    assertThat(timerCount("connection-lease-total")).isEqualTo(2);
    assertMillis("repository-with-connection", 6);
    assertMillis("pre-repository", 0);
    assertThat(coverage("calls", "matched")).isEqualTo(1);
  }

  @Test void incompleteAndUnexpectedSequencesAreNotSynthesizedAsZeroSamples() {
    try (var scope = diagnostics.openScope()) {
      var listener = new DiscoverySessionEventListener();
      listener.jdbcConnectionAcquisitionStart(); listener.jdbcConnectionAcquisitionEnd();
      listener.jdbcConnectionAcquisitionStart(); listener.jdbcConnectionAcquisitionEnd();
      listener.jdbcConnectionReleaseEnd();
    }
    assertThat(timerCount("connection-lease-total")).isZero();
    assertThat(coverage("pairs", "unmatched")).isEqualTo(2);
    assertThat(coverage("calls", "unmatched")).isEqualTo(1);
  }

  @Test void rollbackAndExceptionCleanTheScopeBeforeThreadReuse() {
    assertThatThrownBy(() -> {
      var tx = new SimpleTransactionStatus();
      try (var scope = diagnostics.openScope()) {
        diagnostics.beforeBegin(tx);
        var listener = new DiscoverySessionEventListener();
        listener.jdbcConnectionAcquisitionStart(); listener.jdbcConnectionAcquisitionEnd();
        diagnostics.afterBegin(tx, null);
        DiscoveryLifecycleDiagnostics.repositoryEnter(); advance(2);
        DiscoveryLifecycleDiagnostics.repositoryExit();
        diagnostics.beforeRollback(tx); advance(3); diagnostics.afterRollback(tx, null);
        listener.jdbcConnectionReleaseStart(); listener.jdbcConnectionReleaseEnd();
        throw new IllegalArgumentException("fixture");
      }
    }).isInstanceOf(IllegalArgumentException.class);
    assertThat(DiscoveryLifecycleDiagnostics.currentScope()).isNull();
    assertMillis("transaction-rollback", 3);
    try (var scope = diagnostics.openScope()) { assertThat(scope).isNotNull(); }
    assertThat(DiscoveryLifecycleDiagnostics.currentScope()).isNull();
  }

  @Test void concurrentCallsAndReusedWorkerDoNotShareContext() throws Exception {
    try (var executor = Executors.newFixedThreadPool(2)) {
      var futures = executor.invokeAll(java.util.stream.IntStream.range(0, 20)
          .<java.util.concurrent.Callable<Boolean>>mapToObj(i -> () -> {
            assertThat(DiscoveryLifecycleDiagnostics.currentScope()).isNull();
            try (var scope = diagnostics.openScope()) {
              var listener = new DiscoverySessionEventListener();
              listener.jdbcConnectionAcquisitionStart(); listener.jdbcConnectionAcquisitionEnd();
              listener.jdbcConnectionReleaseStart(); listener.jdbcConnectionReleaseEnd();
            }
            return DiscoveryLifecycleDiagnostics.currentScope() == null;
          }).toList());
      for (var future : futures) assertThat(future.get()).isTrue();
    }
    assertThat(coverage("pairs", "matched")).isEqualTo(20);
  }

  @Test void nestedScopesRestoreOuterContextAndRejectCrossCallAttribution() {
    try (var outer = diagnostics.openScope()) {
      try (var inner = diagnostics.openScope()) { assertThat(inner).isNotSameAs(outer); }
      assertThat(DiscoveryLifecycleDiagnostics.currentScope()).isSameAs(outer);
    }
    assertThat(DiscoveryLifecycleDiagnostics.currentScope()).isNull();
    assertThat(coverage("calls", "unmatched")).isEqualTo(2);
  }

  @Test void registrationPreservesManagerAndOtherListenersAndRejectsSessionConflict() {
    var configuration = new DiscoveryLifecycleConfiguration();
    var manager = new org.springframework.jdbc.datasource.DataSourceTransactionManager();
    var existing = new org.springframework.transaction.TransactionExecutionListener() {};
    manager.addListener(existing);
    configuration.attachDiscoveryTransactionListener(manager, diagnostics).afterSingletonsInstantiated();
    assertThat(manager.getTransactionExecutionListeners()).containsExactly(existing, diagnostics.transactionListener());
    var properties = new java.util.HashMap<String, Object>();
    configuration.discoverySessionEvents().customize(properties);
    assertThat(properties.get(org.hibernate.cfg.SessionEventSettings.AUTO_SESSION_EVENTS_LISTENER))
        .isEqualTo(DiscoverySessionEventListener.class.getName());
    assertThatThrownBy(() -> configuration.discoverySessionEvents().customize(properties))
        .isInstanceOf(IllegalStateException.class);
  }

  private void advance(long millis) { clock.add(Duration.ofMillis(millis)); }
  private long timerCount(String phase) { return registry.get(DiscoveryLifecycleDiagnostics.METRIC).tag("phase", phase).timer().count(); }
  private double coverage(String unit, String result) { return registry.get(DiscoveryLifecycleDiagnostics.METRIC + "." + unit).tag("result", result).counter().count(); }
  private void assertMillis(String phase, double expected) {
    assertThat(registry.get(DiscoveryLifecycleDiagnostics.METRIC).tag("phase", phase).timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(expected);
  }
}
