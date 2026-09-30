package com.pawcycle.backend.catalog.product.diagnostics;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;

/** Call-local observations only; never obtains a connection or changes a transaction. */
@Component
public class DiscoveryLifecycleDiagnostics {
  public static final String METRIC = "pawcycle.catalog.product.discovery.lifecycle";
  public static final List<String> PHASES = List.of(
      "transaction-begin", "transaction-commit", "transaction-rollback",
      "transaction-completion", "transaction-cleanup", "connection-acquire",
      "pre-repository", "repository-with-connection", "repository-body-paired",
      "post-repository-connection-hold", "connection-release", "connection-lease-total");
  private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
  private final boolean enabled;
  private final Clock clock;
  private final Map<String, Timer> timers = new LinkedHashMap<>();
  private final Map<String, Counter> coverage = new LinkedHashMap<>();
  // Not a listener bean: Boot must not auto-register it when diagnostics are disabled.
  private final TransactionExecutionListener transactionListener = new TransactionExecutionListener() {
    @Override public void beforeBegin(TransactionExecution tx) { DiscoveryLifecycleDiagnostics.this.beforeBegin(tx); }
    @Override public void afterBegin(TransactionExecution tx, Throwable failure) { DiscoveryLifecycleDiagnostics.this.afterBegin(tx, failure); }
    @Override public void beforeCommit(TransactionExecution tx) { DiscoveryLifecycleDiagnostics.this.beforeCommit(tx); }
    @Override public void afterCommit(TransactionExecution tx, Throwable failure) { DiscoveryLifecycleDiagnostics.this.afterCommit(tx, failure); }
    @Override public void beforeRollback(TransactionExecution tx) { DiscoveryLifecycleDiagnostics.this.beforeRollback(tx); }
    @Override public void afterRollback(TransactionExecution tx, Throwable failure) { DiscoveryLifecycleDiagnostics.this.afterRollback(tx, failure); }
  };

  public DiscoveryLifecycleDiagnostics(MeterRegistry registry,
      @Value("${pawcycle.catalog.product.discovery.diagnostics.enabled:false}") boolean enabled) {
    this.enabled = enabled;
    this.clock = registry.config().clock();
    if (enabled) {
      PHASES.forEach(phase -> timers.put(phase,
          Timer.builder(METRIC).tag("phase", phase).register(registry)));
      for (String unit : List.of("calls", "pairs")) {
        for (String result : List.of("matched", "unmatched")) {
          coverage.put(unit + "/" + result,
              Counter.builder(METRIC + "." + unit).tag("result", result).register(registry));
        }
      }
    }
  }

  public Scope openScope() {
    if (!enabled) return null;
    Scope previous = CURRENT.get();
    Scope scope = new Scope(this, previous);
    if (previous != null) previous.invalid = true;
    CURRENT.set(scope);
    return scope;
  }

  public static void repositoryEnter() {
    Scope scope = CURRENT.get();
    if (scope != null) scope.repositoryEnter();
  }

  public static void repositoryExit() {
    Scope scope = CURRENT.get();
    if (scope != null) scope.repositoryExit();
  }

  static Scope currentScope() { return CURRENT.get(); }
  public TransactionExecutionListener transactionListener() { return transactionListener; }

  public void beforeBegin(TransactionExecution transaction) {
    Scope s = CURRENT.get();
    if (s != null) {
      if (s.transaction != null) s.invalid = true;
      else { s.transaction = transaction; s.beginStart = s.now(); }
    }
  }
  public void afterBegin(TransactionExecution transaction, Throwable failure) {
    Scope s = CURRENT.get();
    if (s != null && s.transaction == transaction) {
      s.beginEnd = s.now();
      if (failure != null) s.invalid = true;
    }
  }
  public void beforeCommit(TransactionExecution transaction) { completionStart(transaction, "transaction-commit"); }
  public void beforeRollback(TransactionExecution transaction) { completionStart(transaction, "transaction-rollback"); }
  public void afterCommit(TransactionExecution transaction, Throwable failure) { completionEnd(transaction, failure); }
  public void afterRollback(TransactionExecution transaction, Throwable failure) { completionEnd(transaction, failure); }

  private void completionStart(TransactionExecution transaction, String phase) {
    Scope s = CURRENT.get();
    if (s != null && s.transaction == transaction) {
      if (s.completionStart != null) s.invalid = true;
      else { s.completionStart = s.now(); s.completionPhase = phase; }
    }
  }
  private void completionEnd(TransactionExecution transaction, Throwable failure) {
    Scope s = CURRENT.get();
    if (s != null && s.transaction == transaction) {
      s.completionEnd = s.now();
      if (failure != null) s.invalid = true;
    }
  }

  private void record(String phase, long duration) {
    timers.get(phase).record(duration, TimeUnit.NANOSECONDS);
  }

  private static final class Pair {
    Long acquisitionStart, acquisitionEnd, releaseStart, releaseEnd;
    boolean invalid;
    boolean complete() {
      return !invalid && acquisitionStart != null && acquisitionEnd != null
          && releaseStart != null && releaseEnd != null
          && acquisitionStart <= acquisitionEnd && acquisitionEnd <= releaseStart
          && releaseStart <= releaseEnd;
    }
  }
  private record Body(long start, long end) {}

  public static final class Scope implements AutoCloseable {
    private final DiscoveryLifecycleDiagnostics owner;
    private final Scope previous;
    private final List<Pair> pairs = new ArrayList<>();
    private final List<Body> bodies = new ArrayList<>();
    private Pair active;
    private Long bodyStart, beginStart, beginEnd, completionStart, completionEnd;
    private TransactionExecution transaction;
    private String completionPhase;
    private boolean invalid, closed;

    private Scope(DiscoveryLifecycleDiagnostics owner, Scope previous) {
      this.owner = owner;
      this.previous = previous;
      invalid = previous != null;
    }
    private long now() { return owner.clock.monotonicTime(); }
    private void repositoryEnter() {
      if (bodyStart != null) invalid = true;
      else bodyStart = now();
    }
    private void repositoryExit() {
      if (bodyStart == null) invalid = true;
      else { bodies.add(new Body(bodyStart, now())); bodyStart = null; }
    }
    void acquisitionStart() {
      if (active != null) active.invalid = true;
      active = new Pair();
      active.acquisitionStart = now();
      pairs.add(active);
    }
    void acquisitionEnd() {
      if (active == null || active.acquisitionEnd != null) invalid = true;
      else active.acquisitionEnd = now();
    }
    void releaseStart() {
      if (active == null || active.releaseStart != null) invalid = true;
      else active.releaseStart = now();
    }
    void releaseEnd() {
      if (active == null || active.releaseStart == null) invalid = true;
      else { active.releaseEnd = now(); active = null; }
    }

    @Override public void close() {
      if (closed) return;
      closed = true;
      long end = now();
      try {
        boolean completeCall = !invalid && bodyStart == null && !bodies.isEmpty()
            && beginStart != null && beginEnd != null && beginStart <= beginEnd
            && completionStart != null && completionEnd != null
            && beginEnd <= completionStart && completionStart <= completionEnd
            && completionEnd <= end && !pairs.isEmpty() && pairs.stream().allMatch(Pair::complete);
        owner.coverage.get("calls/" + (completeCall ? "matched" : "unmatched")).increment();
        for (Pair pair : pairs) {
          boolean matched = !invalid && pair.complete();
          owner.coverage.get("pairs/" + (matched ? "matched" : "unmatched")).increment();
          if (matched) recordPair(pair);
        }
        if (completeCall) {
          for (Body body : bodies) owner.record("repository-body-paired", body.end - body.start);
          owner.record("transaction-begin", beginEnd - beginStart);
          owner.record(completionPhase, completionEnd - completionStart);
          owner.record("transaction-completion", end - completionStart);
          owner.record("transaction-cleanup", end - completionEnd);
        }
      } finally {
        CURRENT.remove();
        if (previous != null) CURRENT.set(previous);
      }
    }

    private void recordPair(Pair pair) {
      owner.record("connection-acquire", pair.acquisitionEnd - pair.acquisitionStart);
      owner.record("connection-release", pair.releaseEnd - pair.releaseStart);
      owner.record("connection-lease-total", pair.releaseEnd - pair.acquisitionEnd);
      // Measure intersections; acquisition is permitted before or inside a repository body.
      if (bodyStart == null && !bodies.isEmpty()) {
        long first = bodies.getFirst().start;
        long last = bodies.getLast().end;
        long overlap = 0;
        for (Body body : bodies) overlap += Math.max(0,
            Math.min(pair.releaseStart, body.end) - Math.max(pair.acquisitionEnd, body.start));
        owner.record("pre-repository", Math.max(0, Math.min(first, pair.releaseStart) - pair.acquisitionEnd));
        owner.record("repository-with-connection", overlap);
        owner.record("post-repository-connection-hold", Math.max(0,
            pair.releaseStart - Math.max(last, pair.acquisitionEnd)));
      }
    }
  }
}
