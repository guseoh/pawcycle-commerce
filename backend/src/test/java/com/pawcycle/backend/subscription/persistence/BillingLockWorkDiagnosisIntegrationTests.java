package com.pawcycle.backend.subscription.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.support.SecondaryReadFixtures;
import com.pawcycle.backend.support.MysqlReadView;
import com.pawcycle.backend.support.MysqlLockObservation;
import jakarta.persistence.EntityManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Diagnostic only: original T10 assertions and production code remain untouched. */
@SpringBootTest(properties = {
    "spring.datasource.hikari.maximum-pool-size=6",
    "spring.datasource.hikari.minimum-idle=0",
    "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.pawcycle.backend.subscription.persistence.BillingLockWorkDiagnosisIntegrationTests$SqlRecorder"})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class BillingLockWorkDiagnosisIntegrationTests {
  private static final String SQL = """
      SELECT payment.id,payment.order_id,payment.provider_order_id,payment.amount,payment.status,
             method.billing_key,context.schedule_id,context.subscription_id,orders.member_id
      FROM payments payment
      JOIN orders orders ON orders.id=payment.order_id
      JOIN subscription_order_context context ON context.order_id=payment.order_id
      LEFT JOIN billing_payment_methods method ON method.member_id=orders.member_id AND method.status='ACTIVE'
      WHERE payment.id=? FOR UPDATE
      """;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entities;
  @Autowired DataSource dataSource;
  @Autowired PlatformTransactionManager manager;
  @Autowired SubscriptionBillingPersistence current;
  private final Path output = Path.of("build/reports/t10-lock-diagnosis", java.util.UUID.randomUUID().toString());

  public static class SqlRecorder implements StatementInspector {
    static final ThreadLocal<List<String>> statements = ThreadLocal.withInitial(ArrayList::new);
    @Override public String inspect(String sql) { statements.get().add(sql); return sql; }
  }

  @BeforeEach
  void physicalObservationAccess() {
    MysqlLockObservation.requireAccess(jdbc);
  }

  @EnabledIfSystemProperty(named = "pawcycle.t10.diagnoseHistory", matches = "true")
  @ParameterizedTest
  @ValueSource(strings = {"A", "B", "C"})
  void unpinnedSeparateAndSameTransactionComparisons(String kind) throws Exception {
    Fixture f = fixture(kind);
    StringBuilder evidence = new StringBuilder("fixture created=" + f + " at " + Instant.now() + "\n");
    Throwable primary = null;
    try {
      changeFixture(f);
      evidence.append(header(f, "unPinned"));
      List<Map<String, Object>> legacy1 = observe(f, false, evidence);
      List<Map<String, Object>> legacy2 = observe(f, false, evidence);
      List<Map<String, Object>> jpa = observe(f, true, evidence);
      evidence.append("Legacy-Legacy exact equality=").append(legacy1.equals(legacy2)).append('\n');
      evidence.append("Legacy-JPA exact equality=").append(legacy2.equals(jpa)).append('\n');
      // Raw differences are retained as diagnostic output, never converted into a PASS assertion.
      sameTransaction(f, false, evidence);
      sameTransaction(f, true, evidence);
    } catch (Exception | AssertionError failure) {
      primary = failure;
      appendFailure(evidence, failure);
      throw failure;
    } finally {
      try {
        cleanupPairFixture(f);
        evidence.append("fixture cleanup complete=").append(Instant.now()).append('\n');
      } catch (Exception | AssertionError cleanup) {
        appendFailure(evidence, cleanup);
        if (primary != null) primary.addSuppressed(cleanup); else throw cleanup;
      } finally { write(kind + "-unpinned.txt", evidence); }
    }
  }

  @EnabledIfSystemProperty(named = "pawcycle.t10.diagnoseHistory", matches = "true")
  @ParameterizedTest
  @MethodSource("pairOwners")
  void seoulBUnpinnedLegacyFirstPairs(String second) throws Exception {
    diagnoseBPair(second, false);
  }

  @EnabledIfSystemProperty(named = "pawcycle.t10.diagnoseHistory", matches = "true")
  @Test
  void seoulBReleasedViewLegacyFirstPairs() throws Exception {
    var legacy = diagnoseBPair("Legacy", true);
    var jpa = diagnoseBPair("JPA", true);
    StringBuilder evidence = new StringBuilder("release Oracle: Legacy-Legacy versus Legacy-JPA\n")
        .append("legacy=").append(legacy).append("\njpa=").append(jpa).append('\n');
    try {
      assertThat(jpa.proof()).isEqualTo(legacy.proof());
      verifyOracleRejection(jpa, evidence);
      evidence.append("transition equality and regression rejection PASS\n");
    } catch (Exception | AssertionError failure) {
      appendFailure(evidence, failure); throw failure;
    } finally { write("release-oracle.txt", evidence); }
  }

  static java.util.stream.Stream<String> pairOwners() {
    String owner = System.getProperty("pawcycle.t10.pair");
    if (owner == null) return java.util.stream.Stream.of("Legacy", "JPA");
    if (!List.of("Legacy", "JPA").contains(owner)) throw new IllegalArgumentException("Unknown diagnostic pair owner");
    return java.util.stream.Stream.of(owner);
  }

  private ReleaseRun diagnoseBPair(String second, boolean releaseView) throws Exception {
    Fixture f = fixture("B");
    String name = "B-" + (releaseView ? "released-view" : "unpinned") + "-Legacy-" + second + ".txt";
    StringBuilder evidence = new StringBuilder("pair begins=" + Instant.now() + "\n");
    MysqlReadView pin = null;
    Throwable primary = null;
    try (var executor = Executors.newFixedThreadPool(2)) {
      if (releaseView) {
        evidence.append("create independent read view before DELETE at ").append(Instant.now()).append('\n');
        pin = MysqlReadView.open(dataSource);
        evidence.append("pinConnection=").append(pin.connectionId()).append('\n');
      } else evidence.append("no explicit read view; holder performs only locking user-table reads\n");
      evidence.append("DELETE begins=").append(Instant.now()).append('\n');
      changeFixture(f);
      evidence.append("DELETE committed=").append(Instant.now()).append('\n').append(header(f, name));
      var beforeState = fixtureState(f);
      evidence.append("state before holder=").append(beforeState).append('\n');
      var contenderId = new AtomicLong();
      var ready = new CountDownLatch(1);
      var future = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<Integer>>();
      MysqlReadView heldView = pin;
      var comparison = rollback(() -> {
        var first = capturePairCall(f, false, "first", evidence);
        if (releaseView) assertThat(hasRecord(first.locks(), f.member() + ", " + f.method())).isTrue();
        future.set(executor.submit(() -> insert(f.member(), contenderId, ready)));
        try {
          assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
          var firstWait = awaitWait(contenderId.get());
          evidence.append("first INSERT wait at ").append(Instant.now()).append('=').append(firstWait).append('\n');
          var control = executor.submit(() -> insert(f.control(), new AtomicLong(), new CountDownLatch(0)));
          assertThat(control.get(5, TimeUnit.SECONDS)).isEqualTo(1);
          evidence.append("different-member INSERT=1, rollback, holder still active at ").append(Instant.now()).append('\n');
          assertThat(future.get().isDone()).isFalse();
          List<Map<String, Object>> middle = first.locks();
          Instant releasedAt = null;
          Instant middleAt = null;
          if (heldView != null) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_locks WHERE THREAD_ID=(SELECT THREAD_ID FROM performance_schema.threads WHERE PROCESSLIST_ID=?)", Integer.class, heldView.connectionId())).isZero();
            releasedAt = Instant.now();
            evidence.append("release independent read view at ").append(releasedAt).append('\n');
            heldView.close();
            var startMetrics = historyMetrics();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            var sample = locks();
            int probes = 1;
            while (hasRecord(sample, f.member() + ", " + f.method()) && System.nanoTime() < deadline) {
              Thread.sleep(20);
              sample = locks();
              probes++;
            }
            evidence.append("before second lock call: probes=").append(probes).append(" at ").append(Instant.now())
                .append(" holderLocks=").append(sample).append(" metrics start=").append(startMetrics)
                .append(" metrics end=").append(historyMetrics()).append('\n');
            writeUnchecked(name, evidence);
            assertThat(hasRecord(sample, f.member() + ", " + f.method())).as("engine transition before any second lockWork call").isFalse();
            middle = sample;
            middleAt = Instant.now();
          }
          var secondLocks = capturePairCall(f, second.equals("JPA"), "second", evidence);
          var secondWait = awaitWait(contenderId.get());
          evidence.append("second INSERT wait at ").append(Instant.now()).append('=').append(secondWait).append('\n');
          assertThat(future.get().isDone()).isFalse();
          assertThat(firstWait).allSatisfy(row -> assertWait(row));
          assertThat(secondWait).allSatisfy(row -> assertWait(row));
          evidence.append("first/second exact equality=").append(first.locks().equals(secondLocks.locks())).append('\n');
          writeUnchecked(name, evidence);
          return new PairObservation(first, middle, secondLocks, firstWait, secondWait, releasedAt, middleAt);
        } catch (InterruptedException failure) {
          Thread.currentThread().interrupt(); throw new IllegalStateException(failure);
        } catch (Exception failure) { throw new IllegalStateException(failure); }
      });
      evidence.append("holder rollback finished=").append(Instant.now()).append('\n');
      int inserted = future.get().get(10, TimeUnit.SECONDS);
      evidence.append("same-member INSERT resumed affectedRows=").append(inserted).append(" and rolled back at ").append(Instant.now()).append('\n');
      assertThat(inserted).isEqualTo(1);
      var afterState = fixtureState(f);
      evidence.append("state after holder/contenders=").append(afterState).append('\n');
      assertThat(afterState).isEqualTo(beforeState);
      write(name, evidence);
      if (!releaseView) {
        assertThat(comparison.second().locks()).isEqualTo(comparison.first().locks());
        return null;
      }
      var proof = releaseOracle(f, comparison);
      evidence.append("release transition Oracle=").append(proof).append('\n');
      return new ReleaseRun(f, comparison, proof);
    } catch (Exception | AssertionError failure) {
      primary = failure;
      appendFailure(evidence, failure);
      throw failure;
    } finally {
      try {
        if (pin != null) { pin.close(); evidence.append("read view cleanup complete=").append(Instant.now()).append('\n'); }
        cleanupPairFixture(f);
        evidence.append("fixture cleanup complete=").append(Instant.now()).append('\n');
      } catch (Exception | AssertionError cleanup) {
        appendFailure(evidence, cleanup);
        if (primary != null) primary.addSuppressed(cleanup); else throw cleanup;
      } finally { write(name, evidence); }
    }
  }

  private CallObservation capturePairCall(Fixture f, boolean jpa, String phase, StringBuilder evidence) {
    Instant begins = Instant.now();
    var session = session();
    long watermark = jdbc.queryForObject("SELECT COALESCE(MAX(EVENT_ID),0) FROM performance_schema.events_statements_history WHERE THREAD_ID=(SELECT THREAD_ID FROM performance_schema.threads WHERE PROCESSLIST_ID=CONNECTION_ID())", Long.class);
    evidence.append(phase).append(" begin=").append(begins).append(" owner=").append(jpa ? "JPA" : "Legacy")
        .append(" session=").append(session).append(" statement watermark=").append(watermark)
        .append(" flushMode=").append(entities.getFlushMode()).append('\n');
    SqlRecorder.statements.get().clear();
    var work = lock(f.payment(), jpa);
    var statements = jdbc.queryForList("SELECT THREAD_ID,EVENT_ID,SQL_TEXT,DIGEST_TEXT,MYSQL_ERRNO,ROWS_SENT FROM performance_schema.events_statements_history WHERE THREAD_ID=(SELECT THREAD_ID FROM performance_schema.threads WHERE PROCESSLIST_ID=CONNECTION_ID()) AND SQL_TEXT LIKE '%FROM payments payment%' AND SQL_TEXT NOT LIKE '%performance_schema.%' ORDER BY EVENT_ID DESC LIMIT 1");
    var physical = locks();
    Instant observedAt = Instant.now();
    evidence.append(phase).append(" observed=").append(observedAt).append(" actual server statement=").append(statements)
        .append(" Hibernate statements=").append(SqlRecorder.statements.get()).append(" locks=").append(physical).append('\n');
    var identities = jdbc.queryForList("SELECT DISTINCT ENGINE_TRANSACTION_ID,THREAD_ID FROM performance_schema.data_locks WHERE THREAD_ID=(SELECT THREAD_ID FROM performance_schema.threads WHERE PROCESSLIST_ID=CONNECTION_ID())");
    String plan = jdbc.queryForObject("EXPLAIN FORMAT=JSON " + SQL, String.class, f.payment());
    evidence.append(phase).append(" transaction=").append(jdbc.queryForList("SELECT trx_id,trx_state,trx_mysql_thread_id,trx_isolation_level,trx_is_read_only,trx_rows_locked,trx_rows_modified FROM information_schema.innodb_trx WHERE trx_mysql_thread_id=CONNECTION_ID()"))
        .append(" lock identities=").append(identities).append(" EXPLAIN=").append(plan)
        .append(" history=").append(historyMetrics()).append('\n');
    writeUnchecked("pair-progress.txt", evidence);
    assertThat(work.billingKey()).isNull();
    assertThat(work.id()).isEqualTo(f.payment());
    assertThat(statements).hasSize(1);
    assertThat(((Number) statements.getFirst().get("EVENT_ID")).longValue()).as("actual call on the observed connection, not a previous statement").isGreaterThan(watermark);
    assertThat(normalize((String) statements.getFirst().get("SQL_TEXT"))).isEqualTo(normalize(SQL.replace("?", Long.toString(f.payment()))));
    assertThat(identities).hasSize(1);
    assertThat(statements.getFirst().get("THREAD_ID")).isEqualTo(identities.getFirst().get("THREAD_ID"));
    return new CallObservation(physical, plan, ((Number) session.get("connection_id")).longValue(), identities,
        ((Number) statements.getFirst().get("EVENT_ID")).longValue(), watermark, begins, observedAt);
  }

  private TransitionProof releaseOracle(Fixture f, PairObservation pair) {
    var retained = expectedReleaseLocks(f, false);
    assertThat(pair.first().locks()).as("complete pinned first footprint").isEqualTo(expectedReleaseLocks(f, true));
    assertThat(pair.middle()).as("complete released footprint BEFORE second call").isEqualTo(retained);
    assertThat(pair.second().locks()).as("second call must preserve every released lock").isEqualTo(retained);
    assertThat(pair.second().connection()).isEqualTo(pair.first().connection());
    assertThat(pair.second().identities()).isEqualTo(pair.first().identities());
    assertThat(pair.second().plan()).isEqualTo(pair.first().plan());
    assertThat(pair.first().event()).isGreaterThan(pair.first().watermark());
    assertThat(pair.second().event()).isGreaterThan(pair.second().watermark()).isGreaterThan(pair.first().event());
    assertThat(pair.releasedAt()).isAfter(pair.first().observedAt());
    assertThat(pair.middleAt()).isAfterOrEqualTo(pair.releasedAt());
    assertThat(pair.second().begins()).isAfterOrEqualTo(pair.middleAt());
    var removed = pair.first().locks().stream().filter(row -> !pair.middle().contains(row)).toList();
    var added = pair.middle().stream().filter(row -> !pair.first().locks().contains(row)).toList();
    assertThat(removed).containsExactly(lockRow("billing_payment_methods", "fk_billing_payment_methods_member", "RECORD", "X", methodKey(f.member(), f.method())));
    assertThat(added).isEmpty();
    var firstWait = waitRoles(f, pair.firstWait(), pair.first().identities());
    var secondWait = waitRoles(f, pair.secondWait(), pair.second().identities());
    assertThat(secondWait).isEqualTo(firstWait);
    return new TransitionProof(lockRoles(f, pair.first().locks()), lockRoles(f, pair.middle()),
        lockRoles(f, pair.second().locks()), lockRoles(f, removed), added, firstWait, secondWait);
  }

  private List<Map<String, Object>> expectedReleaseLocks(Fixture f, boolean deleted) {
    var result = new ArrayList<Map<String, Object>>();
    result.add(lockRow("billing_payment_methods", null, "TABLE", "IX", null));
    if (deleted) result.add(lockRow("billing_payment_methods", "fk_billing_payment_methods_member", "RECORD", "X", methodKey(f.member(), f.method())));
    result.add(lockRow("billing_payment_methods", "fk_billing_payment_methods_member", "RECORD", "X,GAP", methodKey(f.members().get(2), f.upperMethod())));
    for (String table : List.of("orders", "payments", "subscription_order_context")) {
      result.add(lockRow(table, null, "TABLE", "IX", null));
      result.add(lockRow(table, "PRIMARY", "RECORD", "X,REC_NOT_GAP", Long.toString(table.equals("payments") ? f.payment() : f.order())));
    }
    return result;
  }

  private Map<String, Object> lockRow(String table, String index, String type, String mode, String data) {
    var row = new java.util.LinkedHashMap<String, Object>();
    row.put("OBJECT_NAME", table); row.put("INDEX_NAME", index); row.put("LOCK_TYPE", type);
    row.put("LOCK_MODE", mode); row.put("LOCK_STATUS", "GRANTED"); row.put("LOCK_DATA", data);
    return row;
  }

  private String methodKey(long member, long method) { return member + ", " + method; }

  // Only already-validated fixture identities receive explicit roles; no lock row/field is dropped.
  private List<Map<String, Object>> lockRoles(Fixture f, List<Map<String, Object>> locks) {
    return locks.stream().map(lock -> {
      var role = new java.util.LinkedHashMap<>(lock);
      if (lock.get("LOCK_DATA") != null) {
        if ("billing_payment_methods".equals(lock.get("OBJECT_NAME"))) {
          role.put("LOCK_DATA", methodKey(f.member(), f.method()).equals(lock.get("LOCK_DATA")) ? "TARGET_MEMBER,DELETED_METHOD" : "UPPER_MEMBER,UPPER_METHOD");
        } else role.put("LOCK_DATA", "payments".equals(lock.get("OBJECT_NAME")) ? "PAYMENT_ID" : "ORDER_ID");
      }
      return (Map<String, Object>) role;
    }).toList();
  }

  private List<Map<String, Object>> waitRoles(Fixture f, List<Map<String, Object>> waits, List<Map<String, Object>> identities) {
    assertThat(waits).hasSize(1);
    var row = waits.getFirst();
    assertWait(row);
    assertThat(row.get("REQUEST_TYPE")).isEqualTo("RECORD");
    assertThat(row.get("REQUEST_MODE")).isEqualTo("X,GAP,INSERT_INTENTION");
    assertThat(row.get("BLOCK_MODE")).isEqualTo("X,GAP");
    assertThat(row.get("REQUEST_DATA")).isEqualTo(methodKey(f.members().get(2), f.upperMethod()));
    assertThat(row.get("BLOCK_DATA")).isEqualTo(row.get("REQUEST_DATA"));
    assertThat(row.get("blocker_transaction")).isEqualTo(identities.getFirst().get("ENGINE_TRANSACTION_ID"));
    var role = new java.util.LinkedHashMap<>(row);
    role.remove("request_transaction"); role.remove("blocker_transaction");
    role.put("REQUEST_DATA", "UPPER_MEMBER,UPPER_METHOD"); role.put("BLOCK_DATA", "UPPER_MEMBER,UPPER_METHOD");
    return List.of(role);
  }

  private void verifyOracleRejection(ReleaseRun run, StringBuilder evidence) {
    var p = run.pair();
    for (String column : List.of("INDEX_NAME", "LOCK_MODE", "LOCK_STATUS", "LOCK_TYPE", "LOCK_DATA")) {
      var wrong = new ArrayList<>(p.second().locks());
      var changed = new java.util.LinkedHashMap<>(wrong.get(1));
      changed.put(column, "REGRESSION"); wrong.set(1, changed);
      rejectOracle(run, withSecondLocks(p, wrong), "changed successor " + column, evidence);
    }
    var missing = new ArrayList<>(p.second().locks()); missing.remove(5);
    rejectOracle(run, withSecondLocks(p, missing), "missing payment PRIMARY lock", evidence);
    var extra = new ArrayList<>(p.second().locks()); extra.add(lockRow("billing_payment_methods", "PRIMARY", "RECORD", "X", "unexpected"));
    rejectOracle(run, withSecondLocks(p, extra), "extra record lock", evidence);
    var wrongWait = new java.util.LinkedHashMap<>(p.secondWait().getFirst()); wrongWait.put("REQUEST_STATUS", "GRANTED");
    rejectOracle(run, new PairObservation(p.first(), p.middle(), p.second(), p.firstWait(), List.of(wrongWait), p.releasedAt(), p.middleAt()), "contender no longer waiting", evidence);
    var wrongConnection = new CallObservation(p.second().locks(), p.second().plan(), p.second().connection() + 1, p.second().identities(), p.second().event(), p.second().watermark(), p.second().begins(), p.second().observedAt());
    rejectOracle(run, new PairObservation(p.first(), p.middle(), wrongConnection, p.firstWait(), p.secondWait(), p.releasedAt(), p.middleAt()), "connection changed", evidence);
    var stale = new CallObservation(p.second().locks(), p.second().plan(), p.second().connection(), p.second().identities(), p.first().event(), p.second().watermark(), p.second().begins(), p.second().observedAt());
    rejectOracle(run, new PairObservation(p.first(), p.middle(), stale, p.firstWait(), p.secondWait(), p.releasedAt(), p.middleAt()), "missing second SQL execution despite inherited locks", evidence);
  }

  private PairObservation withSecondLocks(PairObservation p, List<Map<String, Object>> locks) {
    var call = p.second();
    return new PairObservation(p.first(), p.middle(), new CallObservation(locks, call.plan(), call.connection(), call.identities(), call.event(), call.watermark(), call.begins(), call.observedAt()), p.firstWait(), p.secondWait(), p.releasedAt(), p.middleAt());
  }

  private void rejectOracle(ReleaseRun run, PairObservation changed, String scenario, StringBuilder evidence) {
    org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class, () -> releaseOracle(run.fixture(), changed), scenario);
    evidence.append("rejected: ").append(scenario).append('\n');
  }

  private record CallObservation(List<Map<String, Object>> locks, String plan, long connection,
      List<Map<String, Object>> identities, long event, long watermark, Instant begins, Instant observedAt) {}
  private record PairObservation(CallObservation first, List<Map<String, Object>> middle, CallObservation second,
      List<Map<String, Object>> firstWait, List<Map<String, Object>> secondWait, Instant releasedAt, Instant middleAt) {}
  private record TransitionProof(List<Map<String, Object>> first, List<Map<String, Object>> middle, List<Map<String, Object>> second,
      List<Map<String, Object>> removed, List<Map<String, Object>> added, List<Map<String, Object>> firstWait, List<Map<String, Object>> secondWait) {}
  private record ReleaseRun(Fixture fixture, PairObservation pair, TransitionProof proof) {}

  private List<Map<String, Object>> historyMetrics() {
    return jdbc.queryForList("SELECT NAME,COUNT,STATUS FROM information_schema.INNODB_METRICS WHERE NAME IN ('trx_rseg_history_len','purge_del_mark_records')");
  }

  private Map<String, Object> fixtureState(Fixture f) {
    return jdbc.queryForMap("SELECT p.status payment_status,p.amount,p.attempt_no,o.status order_status,c.schedule_id,s.status schedule_status,i.available_quantity,i.reserved_quantity,(SELECT COUNT(*) FROM billing_payment_methods WHERE member_id=o.member_id) method_count,(SELECT COUNT(*) FROM inventory_movements WHERE sku_id=i.sku_id) movement_count FROM payments p JOIN orders o ON o.id=p.order_id JOIN subscription_order_context c ON c.order_id=o.id JOIN subscription_schedules s ON s.id=c.schedule_id JOIN subscriptions sub ON sub.id=c.subscription_id JOIN inventories i ON i.sku_id=sub.sku_id WHERE p.id=?", f.payment());
  }

  private void cleanupPairFixture(Fixture f) {
    new TransactionTemplate(manager).executeWithoutResult(status -> {
      jdbc.update("DELETE FROM payments WHERE id=?", f.payment());
      jdbc.update("DELETE FROM subscription_order_context WHERE order_id=?", f.order());
      jdbc.update("DELETE FROM orders WHERE id=?", f.order());
      jdbc.update("DELETE FROM subscription_schedules WHERE id=?", f.schedule());
      jdbc.update("DELETE FROM subscriptions WHERE id=?", f.subscription());
      for (long member : f.members()) jdbc.update("DELETE FROM billing_payment_methods WHERE member_id=?", member);
      jdbc.update("DELETE FROM inventories WHERE sku_id=?", f.sku());
      jdbc.update("DELETE FROM skus WHERE id=?", f.sku());
      jdbc.update("DELETE FROM products WHERE id=?", f.product());
      jdbc.update("DELETE FROM categories WHERE id=?", f.category());
      jdbc.update("DELETE FROM brands WHERE id=?", f.brand());
      for (long member : f.members()) jdbc.update("DELETE FROM members WHERE id=?", member);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE id=?", Integer.class, f.payment())).isZero();
    });
  }

  private void assertWait(Map<String, Object> row) {
    assertThat(row.get("OBJECT_NAME")).isEqualTo("billing_payment_methods");
    assertThat(row.get("INDEX_NAME")).isEqualTo("fk_billing_payment_methods_member");
    assertThat(row.get("REQUEST_STATUS")).isEqualTo("WAITING");
    assertThat(row.get("BLOCK_STATUS")).isEqualTo("GRANTED");
  }

  private void appendFailure(StringBuilder evidence, Throwable failure) {
    var text = new java.io.StringWriter();
    failure.printStackTrace(new java.io.PrintWriter(text));
    evidence.append("exception at ").append(Instant.now()).append('\n').append(text).append('\n');
  }

  private void writeUnchecked(String name, StringBuilder evidence) {
    try { write(name, evidence); } catch (Exception failure) { throw new IllegalStateException("Cannot retain diagnostic evidence", failure); }
  }

  @ParameterizedTest
  @ValueSource(strings = {"A", "B", "C"})
  void fixedReadViewHistoryAndBothInsertBlockingPaths(String kind) throws Exception {
    Fixture f = fixture(kind);
    try (MysqlReadView pin = MysqlReadView.open(dataSource)) {
      changeFixture(f);
      StringBuilder evidence = header(f, "fixedReadView");
      evidence.append("pinConnection=").append(pin.connectionId()).append('\n');
      List<Map<String, Object>> old1 = observe(f, false, evidence);
      List<Map<String, Object>> old2 = observe(f, false, evidence);
      List<Map<String, Object>> jpa = observe(f, true, evidence);
      evidence.append("Legacy-Legacy exact equality=").append(old1.equals(old2)).append('\n');
      evidence.append("Legacy-JPA exact equality=").append(old2.equals(jpa)).append('\n');
      sameTransaction(f, false, evidence);
      sameTransaction(f, true, evidence);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_locks WHERE THREAD_ID=(SELECT THREAD_ID FROM performance_schema.threads WHERE PROCESSLIST_ID=?)", Integer.class, pin.connectionId())).isZero();
      var legacyBlocking = blocking(f, false, evidence);
      var jpaBlocking = blocking(f, true, evidence);
      evidence.append("blocking exact table/index/mode equality=").append(legacyBlocking.equals(jpaBlocking)).append('\n');
      write(kind + "-fixed-view.txt", evidence);
      assertThat(old1).as(kind + " Legacy-Legacy fixed physical history").isEqualTo(old2);
      assertThat(jpa).as(kind + " Legacy-JPA fixed physical history").isEqualTo(old2);
      assertThat(jpaBlocking).isEqualTo(legacyBlocking);
    }
  }

  @EnabledIfSystemProperty(named = "pawcycle.t10.diagnoseHistory", matches = "true")
  @Test
  void deletedRecordReadViewLifetimeSeparatesHistoryFromPersistenceExecution() throws Exception {
    Fixture f = fixture("B");
    try (MysqlReadView pin = MysqlReadView.open(dataSource)) {
      changeFixture(f);
      StringBuilder evidence = header(f, "releaseReadView");
      var before1 = observe(f, false, evidence);
      var before2 = observe(f, false, evidence);
      var beforeJpa = observe(f, true, evidence);
      String deletedIndexKey = f.member() + ", " + f.method();
      assertThat(before1).anySatisfy(lock -> {
        assertThat(lock.get("INDEX_NAME")).isEqualTo("fk_billing_payment_methods_member");
        assertThat(lock.get("LOCK_DATA")).isEqualTo(deletedIndexKey);
      });
      assertThat(before1).isEqualTo(before2).isEqualTo(beforeJpa);
      evidence.append("release pinned snapshot at ").append(Instant.now()).append('\n');
      pin.close();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
      List<Map<String, Object>> after = before1;
      int probes = 0;
      // Observe an engine state transition, not retry a verification failure or normalize locks.
      while (System.nanoTime() < deadline && hasRecord(after, deletedIndexKey)) {
        after = observe(f, false, evidence);
        probes++;
      }
      var afterJpa = observe(f, true, evidence);
      evidence.append("legacy-only probes after read-view release=").append(probes).append('\n');
      evidence.append("Legacy(before)-Legacy(after) exact equality=").append(before1.equals(after)).append('\n');
      evidence.append("Legacy(after)-JPA(after) exact equality=").append(after.equals(afterJpa)).append('\n');
      evidence.append("history metrics=").append(jdbc.queryForList("SELECT NAME,COUNT FROM information_schema.INNODB_METRICS WHERE NAME IN ('trx_rseg_history_len','purge_del_mark_records')")).append('\n');
      write("B-read-view-transition.txt", evidence);
      assertThat(hasRecord(after, deletedIndexKey)).as("deleted secondary record disappears after releasing oldest read view").isFalse();
      assertThat(afterJpa).isEqualTo(after);
    }
  }

  private boolean hasRecord(List<Map<String, Object>> locks, String key) {
    return locks.stream().anyMatch(row -> key.equals(row.get("LOCK_DATA"))
        && "fk_billing_payment_methods_member".equals(row.get("INDEX_NAME")));
  }

  private Fixture fixture(String kind) {
    assertThat(jdbc.queryForObject("SELECT @@version", String.class)).startsWith("8.4.");
    var f = new TransactionTemplate(manager).execute(status -> {
      var sql = new SecondaryReadFixtures(jdbc, entities);
      long lower = sql.member();
      method(lower);
      long member = sql.member();
      long upper = sql.member();
      long upperMethod = method(upper);
      long control = sql.member();
      long upperControl = sql.member();
      method(upperControl);
      var category = sql.category(false);
      var brand = sql.brand(true);
      var product = sql.product(category, brand, "DOG", "PUBLIC");
      long sku = sql.sku(product, SkuStatus.ACTIVE, 10).getId();
      long order = sql.order(member, "SUBSCRIPTION", "PAYMENT_PENDING", new java.math.BigDecimal("1234.56"), null);
      long payment = sql.payment(order, "BILLING", "READY", 1);
      jdbc.update("INSERT INTO subscriptions(member_id,sku_id,quantity,delivery_cycle_weeks,created_date,next_order_date,status,version) VALUES (?,?,1,2,'2026-09-01','2026-09-18','ACTIVE',0)", member, sku);
      long subscription = sql.lastId();
      jdbc.update("INSERT INTO subscription_schedules(subscription_id,scheduled_date,status) VALUES (?,'2026-09-18','SCHEDULED')", subscription);
      long schedule = sql.lastId();
      jdbc.update("INSERT INTO subscription_order_context(order_id,subscription_id,schedule_id,scheduled_date) VALUES (?,?,?,'2026-09-18')", order, subscription, schedule);
      long method = kind.equals("A") ? 0 : method(member);
      return new Fixture(kind, member, control, payment, method,
          List.of(lower, member, upper, control, upperControl), order, subscription, schedule,
          sku, product.getId(), category.getId(), brand.getId(), upperMethod);
    });
    return f;
  }

  private void changeFixture(Fixture f) {
    if (f.kind().equals("B")) jdbc.update("DELETE FROM billing_payment_methods WHERE id=?", f.method());
    if (f.kind().equals("C")) jdbc.update("UPDATE billing_payment_methods SET status='REVOKED',revoked_at=? WHERE id=?", SecondaryReadFixtures.stamp(), f.method());
  }

  private long method(long member) {
    jdbc.update("INSERT INTO billing_payment_methods(member_id,provider,customer_key,billing_key,status,created_at) VALUES (?,'TOSS',?,'diagnostic-fixture','ACTIVE',?)", member, "diagnostic-" + member, SecondaryReadFixtures.stamp());
    return jdbc.queryForObject("SELECT id FROM billing_payment_methods WHERE member_id=?", Long.class, member);
  }

  private StringBuilder header(Fixture f, String mode) {
    return new StringBuilder("fixture=" + f + " mode=" + mode + "\n")
        .append("sequence=anchor members/methods -> target order/payment/context -> ")
        .append(f.kind().equals("A") ? "target method never inserted" : "target ACTIVE method commit -> " + (f.kind().equals("B") ? "DELETE commit" : "REVOKED commit"))
        .append(" -> independent holder transactions; all probes same paymentId\n")
        .append("SQL=").append(SQL).append("binding[1]=Long(").append(f.payment()).append(")\n")
        .append("version/global=").append(jdbc.queryForMap("SELECT @@version version,@@innodb_purge_threads purge_threads,@@innodb_max_purge_lag purge_lag,@@character_set_server charset,@@collation_server collation")).append('\n')
        .append("EXPLAIN JSON=").append(jdbc.queryForObject("EXPLAIN FORMAT=JSON " + SQL, String.class, f.payment())).append('\n')
        .append("history at fixture commit=").append(jdbc.queryForList("SELECT NAME,COUNT FROM information_schema.INNODB_METRICS WHERE NAME IN ('trx_rseg_history_len','purge_del_mark_records')")).append('\n');
  }

  private List<Map<String, Object>> observe(Fixture f, boolean jpa, StringBuilder evidence) {
    return rollback(() -> {
      SqlRecorder.statements.get().clear();
      var session = session();
      evidence.append("EXPLAIN for this bound probe=")
          .append(jdbc.queryForObject("EXPLAIN FORMAT=JSON " + SQL, String.class, f.payment())).append('\n');
      var work = lock(f.payment(), jpa);
      assertThat(work.billingKey()).isNull();
      assertThat(work.id()).isEqualTo(f.payment());
      var locks = locks();
      evidence.append("observe=").append(jpa ? "JPA" : "Legacy").append(" at ").append(Instant.now()).append('\n');
      evidence.append("session=").append(session).append('\n');
      evidence.append("transaction identities=").append(jdbc.queryForList("SELECT DISTINCT ENGINE_TRANSACTION_ID,THREAD_ID FROM performance_schema.data_locks WHERE THREAD_ID=(SELECT THREAD_ID FROM performance_schema.threads WHERE PROCESSLIST_ID=CONNECTION_ID())")).append('\n');
      evidence.append("locks=").append(locks).append('\n');
      if (jpa) {
        var statements = SqlRecorder.statements.get().stream().filter(s -> s.contains("FROM payments payment")).toList();
        evidence.append("actual Hibernate SQL=").append(statements).append('\n');
        assertThat(statements).hasSize(1);
        assertThat(normalize(statements.getFirst())).isEqualTo(normalize(SQL));
      }
      return locks;
    });
  }

  private void sameTransaction(Fixture f, boolean jpaFirst, StringBuilder evidence) {
    rollback(() -> {
      lock(f.payment(), jpaFirst);
      var first = locks();
      lock(f.payment(), !jpaFirst);
      var second = locks();
      evidence.append("sameTransaction first=").append(jpaFirst ? "JPA" : "Legacy")
          .append(" session=").append(session()).append(" exact equality=").append(first.equals(second)).append('\n');
      evidence.append("first=").append(first).append(" second=").append(second).append('\n');
      writeUnchecked("same-transaction-progress.txt", evidence);
      assertThat(second).isEqualTo(first);
      return null;
    });
  }

  private SubscriptionBillingPersistence.BillingWork lock(long id, boolean jpa) {
    return jpa ? current.lockWork(id) : new LegacySubscriptionBillingPersistence(jdbc).lockWork(id);
  }

  private Map<String, Object> session() {
    return jdbc.queryForMap("SELECT CONNECTION_ID() connection_id,@@session.transaction_isolation isolation_level,@@session.autocommit autocommit,@@session.transaction_read_only read_only,@@session.sql_mode sql_mode,@@session.time_zone time_zone,@@session.optimizer_switch optimizer_switch,@@session.innodb_lock_wait_timeout lock_wait_timeout,@@session.foreign_key_checks foreign_key_checks,@@session.unique_checks unique_checks,@@session.character_set_connection charset,@@session.collation_connection collation,NOW(6) server_now,UTC_TIMESTAMP(6) utc_now");
  }

  private List<Map<String, Object>> locks() {
    return jdbc.queryForList("SELECT OBJECT_NAME,INDEX_NAME,LOCK_TYPE,LOCK_MODE,LOCK_STATUS,LOCK_DATA FROM performance_schema.data_locks WHERE OBJECT_SCHEMA=DATABASE() AND THREAD_ID=(SELECT THREAD_ID FROM performance_schema.threads WHERE PROCESSLIST_ID=CONNECTION_ID()) ORDER BY OBJECT_NAME,INDEX_NAME,LOCK_TYPE,LOCK_MODE,LOCK_STATUS,LOCK_DATA");
  }

  private List<Map<String, Object>> blocking(Fixture f, boolean jpa, StringBuilder evidence) throws Exception {
    AtomicLong contenderId = new AtomicLong();
    CountDownLatch ready = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var pending = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<Integer>>();
      var waits = new TransactionTemplate(manager).execute(status -> {
        lock(f.payment(), jpa);
        evidence.append("blocking holder=").append(jpa ? "JPA" : "Legacy").append(" session=").append(session()).append('\n');
        pending.set(executor.submit(() -> insert(f.member(), contenderId, ready)));
        try { assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
        var wait = awaitWait(contenderId.get());
        evidence.append("observed wait=").append(wait).append(" at ").append(Instant.now()).append('\n');
        assertThat(wait).isNotEmpty();
        assertThat(wait).allSatisfy(row -> {
          assertThat(row.get("OBJECT_NAME")).isEqualTo("billing_payment_methods");
          assertThat(row.get("INDEX_NAME")).isEqualTo("fk_billing_payment_methods_member");
          assertThat(row.get("REQUEST_STATUS")).isEqualTo("WAITING");
          assertThat(row.get("BLOCK_STATUS")).isEqualTo("GRANTED");
        });
        var control = executor.submit(() -> insert(f.control(), new AtomicLong(), new CountDownLatch(0)));
        try { assertThat(control.get(5, TimeUnit.SECONDS)).isEqualTo(1); }
        catch (Exception error) { throw new AssertionError("different-member control must complete while holder remains active", error); }
        assertThat(pending.get().isDone()).isFalse();
        evidence.append("other-member INSERT=1, rolled back; same-member INSERT still waiting\n");
        return wait.stream().map(row -> Map.<String, Object>of(
            "OBJECT_NAME", row.get("OBJECT_NAME"), "INDEX_NAME", row.get("INDEX_NAME"),
            "REQUEST_MODE", row.get("REQUEST_MODE"), "BLOCK_MODE", row.get("BLOCK_MODE"))).toList();
      });
      int inserted = pending.get().get(10, TimeUnit.SECONDS);
      evidence.append("holder committed at ").append(Instant.now()).append("; contender resumed affectedRows=").append(inserted).append(" then rollback\n");
      assertThat(inserted).isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM billing_payment_methods WHERE member_id=? AND status='ACTIVE'", Integer.class, f.member())).isZero();
      return waits;
    }
  }

  private List<Map<String, Object>> awaitWait(long contender) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      var waits = jdbc.queryForList("SELECT request.ENGINE_TRANSACTION_ID request_transaction,blocker.ENGINE_TRANSACTION_ID blocker_transaction,request.OBJECT_NAME,request.INDEX_NAME,request.LOCK_TYPE REQUEST_TYPE,request.LOCK_MODE REQUEST_MODE,request.LOCK_STATUS REQUEST_STATUS,request.LOCK_DATA REQUEST_DATA,blocker.LOCK_MODE BLOCK_MODE,blocker.LOCK_STATUS BLOCK_STATUS,blocker.LOCK_DATA BLOCK_DATA FROM performance_schema.data_lock_waits waits JOIN performance_schema.data_locks request ON request.ENGINE_LOCK_ID=waits.REQUESTING_ENGINE_LOCK_ID JOIN performance_schema.data_locks blocker ON blocker.ENGINE_LOCK_ID=waits.BLOCKING_ENGINE_LOCK_ID WHERE request.THREAD_ID=(SELECT THREAD_ID FROM performance_schema.threads WHERE PROCESSLIST_ID=?) AND blocker.THREAD_ID=(SELECT THREAD_ID FROM performance_schema.threads WHERE PROCESSLIST_ID=CONNECTION_ID())", contender);
      if (!waits.isEmpty()) return waits;
      try { Thread.sleep(20); }
      catch (InterruptedException error) {
        Thread.currentThread().interrupt();
        throw new AssertionError("Interrupted waiting for database wait", error);
      }
    }
    throw new AssertionError("No database wait observed for contender " + contender);
  }

  private int insert(long member, AtomicLong connection, CountDownLatch ready) throws SQLException {
    try (Connection c = dataSource.getConnection()) {
      c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
      c.setAutoCommit(false);
      try {
        connection.set(connectionId(c));
        try (var statement = c.createStatement()) { statement.execute("SET SESSION innodb_lock_wait_timeout=10"); }
        ready.countDown();
        try (var query = c.prepareStatement("INSERT INTO billing_payment_methods(member_id,provider,customer_key,billing_key,status,created_at) VALUES (?,'TOSS',?,'diagnostic-contender','ACTIVE','2026-09-18')")) {
          query.setLong(1, member);
          query.setString(2, "contender-" + member);
          int changed = query.executeUpdate();
          try (var check = c.prepareStatement("SELECT COUNT(*) FROM billing_payment_methods WHERE member_id=? AND status='ACTIVE'")) {
            check.setLong(1, member);
            try (var rows = check.executeQuery()) { assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isEqualTo(1); }
          }
          return changed;
        }
      } finally { c.rollback(); }
    }
  }

  private long connectionId(Connection c) throws SQLException {
    try (var query = c.createStatement(); var rows = query.executeQuery("SELECT CONNECTION_ID()")) {
      rows.next(); return rows.getLong(1);
    }
  }

  private <T> T rollback(Supplier<T> action) {
    return new TransactionTemplate(manager).execute(status -> {
      try { return action.get(); } finally { status.setRollbackOnly(); }
    });
  }

  private String normalize(String sql) { return sql.replaceAll("\\s+", " ").trim(); }
  private void write(String name, StringBuilder evidence) throws Exception {
    Files.createDirectories(output);
    Files.writeString(output.resolve(name), evidence.toString());
  }
  private record Fixture(String kind, long member, long control, long payment, long method,
      List<Long> members, long order, long subscription, long schedule, long sku, long product, long category, long brand, long upperMethod) {}
}
