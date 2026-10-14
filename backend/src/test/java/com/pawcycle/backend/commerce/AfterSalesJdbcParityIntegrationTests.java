package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawcycle.backend.commerce.audit.application.AdminAuditService;
import com.pawcycle.backend.commerce.cancellation.application.CancellationService;
import com.pawcycle.backend.commerce.cancellation.persistence.CancellationPersistenceAdapter;
import com.pawcycle.backend.commerce.cancellation.persistence.FrozenCancellationJdbcReference;
import com.pawcycle.backend.commerce.inventory.application.InventoryService;
import com.pawcycle.backend.commerce.notification.application.NotificationService;
import com.pawcycle.backend.commerce.returning.application.ReturnService;
import com.pawcycle.backend.commerce.returning.persistence.ReturnPersistenceAdapter;
import com.pawcycle.backend.commerce.returning.persistence.FrozenReturnJdbcReference;
import com.pawcycle.backend.commerce.refund.persistence.RefundPersistenceAdapter;
import com.pawcycle.backend.commerce.refund.persistence.FrozenRefundJdbcReference;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.domain.MemberAddress;
import com.pawcycle.backend.support.AfterSalesFixtures;
import com.pawcycle.backend.support.AfterSalesFixtures.OrderFixture;
import com.pawcycle.backend.support.ReadQueryParity;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Frozen main SQL, not a second implementation of the domain policies. */
@SpringBootTest
@ActiveProfiles({"test", "local-integration"})
class AfterSalesJdbcParityIntegrationTests {
  private static final Instant NOW = Instant.parse("2026-10-14T00:00:00.123456Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entities;
  @Autowired PlatformTransactionManager manager;
  @Autowired InventoryService inventory;
  @Autowired NotificationService notifications;
  @Autowired AdminAuditService audits;

  @Test
  void cancellationSnapshotCountsIdentityAndOuterRollbackMatchFrozen() {
    var o = fixture("PREPARING");
    Snapshot before = cancellationSnapshot(o, true);
    Snapshot after = cancellationSnapshot(o, false);
    assertThat(after).isEqualTo(before);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_cancellations WHERE order_id=?", Long.class, o.order())).isZero();
    assertThat(jdbc.queryForObject("SELECT status FROM deliveries WHERE order_id=?", String.class, o.order())).isEqualTo("PREPARING");
    tx().executeWithoutResult(s -> {
      var old = cancellation(true); var typed = cancellation(false);
      ReadQueryParity.compare(jdbc, () -> old.findOrderForUpdate(o.member(), o.order()), () -> typed.findOrderForUpdate(o.member(), o.order()));
      ReadQueryParity.compare(jdbc, () -> old.findOrderForUpdate(o.admin(), o.order()), () -> typed.findOrderForUpdate(o.admin(), o.order()));
      ReadQueryParity.compare(jdbc, () -> old.findDeliveryForUpdate(o.order()), () -> typed.findDeliveryForUpdate(o.order()));
      ReadQueryParity.compare(jdbc, () -> old.hasSuccessfulPayment(o.order()), () -> typed.hasSuccessfulPayment(o.order()));
      ReadQueryParity.compare(jdbc, () -> old.hasReturnForUpdate(o.order()), () -> typed.hasReturnForUpdate(o.order()));
      ReadQueryParity.compare(jdbc, () -> old.findOrderItems(o.order()), () -> typed.findOrderItems(o.order()));
      long id = old.create(o.order(), "projection");
      ReadQueryParity.compare(jdbc, () -> old.findExisting(o.order()), () -> typed.findExisting(o.order()));
      ReadQueryParity.compare(jdbc, () -> old.find(id), () -> typed.find(id));
      jdbc.update("UPDATE order_cancellations SET completed_at=? WHERE id=?", Timestamp.from(NOW), id);
      assertThat(ReadQueryParity.compare(jdbc, () -> old.find(id), () -> typed.find(id)).completedAt()).isEqualTo(Timestamp.from(NOW));
      s.setRollbackOnly();
    });
  }

  @Test
  void returnRequestDecisionReceiveAllRestockStatesAndRollbackMatchFrozen() {
    for (boolean restock : List.of(false, true)) {
      var o = fixture("DELIVERED");
      assertThat(returnSnapshot(o, false, restock)).isEqualTo(returnSnapshot(o, true, restock));
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_returns WHERE order_id=?", Long.class, o.order())).isZero();
      tx().executeWithoutResult(s -> {
        var old = returning(true); var typed = returning(false);
        ReadQueryParity.compare(jdbc, () -> old.findOrderForUpdate(o.member(), o.order()), () -> typed.findOrderForUpdate(o.member(), o.order()));
        ReadQueryParity.compare(jdbc, () -> old.hasCancellationForUpdate(o.order()), () -> typed.hasCancellationForUpdate(o.order()));
        ReadQueryParity.compare(jdbc, () -> old.findDeliveryForUpdate(o.order()), () -> typed.findDeliveryForUpdate(o.order()));
        ReadQueryParity.compare(jdbc, () -> old.findOrderItems(o.order()), () -> typed.findOrderItems(o.order()));
        long id = old.create(o.order(), "projection");
        var initial = ReadQueryParity.compare(jdbc, () -> old.findByOrderForUpdate(o.order()), () -> typed.findByOrderForUpdate(o.order()));
        assertThat(initial.restock()).isNull(); assertThat(initial.requestedAt()).isEqualTo(Timestamp.from(NOW));
        ReadQueryParity.compare(jdbc, () -> old.findForDecision(id), () -> typed.findForDecision(id));
        ReadQueryParity.compare(jdbc, () -> old.findForReceive(id), () -> typed.findForReceive(id));
        typed.decide(id, "REJECTED", "reason", o.admin());
        assertThat(ReadQueryParity.compare(jdbc, () -> old.find(id), () -> typed.find(id)).rejectionReason()).isEqualTo("reason");
        old.decide(id, "APPROVED", null, o.admin()); typed.receive(id, restock, o.admin());
        var received = ReadQueryParity.compare(jdbc, () -> old.find(id), () -> typed.find(id));
        assertThat(received.restock()).isEqualTo(restock); assertThat(received.receivedAt()).isEqualTo(Timestamp.from(NOW));
        s.setRollbackOnly();
      });
    }
  }

  @Test
  void refundInsertSelectRetryNullsDecimalReconciliationAndCompletionMatchFrozen() {
    for (String source : List.of("CANCELLATION", "RETURN")) {
      var o = fixture("PREPARING"); long refund = seedRefund(o, source);
      assertThat(refundSnapshot(o, refund, false)).isEqualTo(refundSnapshot(o, refund, true));
      tx().executeWithoutResult(s -> {
        var old = refund(true); var typed = refund(false);
        ReadQueryParity.compare(jdbc, () -> old.findReadyForUpdate(refund), () -> typed.findReadyForUpdate(refund));
        var retry = ReadQueryParity.compare(jdbc, () -> old.findRetryTarget(refund), () -> typed.findRetryTarget(refund));
        assertThat(source.equals("RETURN") ? retry.cancellationId() : retry.returnId()).isNull();
        ReadQueryParity.compare(jdbc, () -> old.findRetry(retry.sourceId(), retry.source(), 2), () -> typed.findRetry(retry.sourceId(), retry.source(), 2));
        ReadQueryParity.compare(jdbc, () -> old.findForReconciliation(refund), () -> typed.findForReconciliation(refund));
        ReadQueryParity.compare(jdbc, () -> old.findForCompletion(refund), () -> typed.findForCompletion(refund));
        ReadQueryParity.compare(jdbc, () -> old.find(refund), () -> typed.find(refund));
        typed.markProcessing(refund); typed.incrementReconciliationAttempts(refund, 1);
        for (String status : List.of("UNKNOWN", "FAILED", "SUCCEEDED")) {
          typed.complete(refund, status, null, null);
          var row = ReadQueryParity.compare(jdbc, () -> old.find(refund), () -> typed.find(refund));
          assertThat(row.completedAt()).isEqualTo(status.equals("SUCCEEDED") ? Timestamp.from(NOW) : null);
          assertThat(row.providerStatus()).isNull(); assertThat(row.failureCode()).isNull();
          assertThat(row.processedAt().getNanos()).isEqualTo(123456000);
        }
        s.setRollbackOnly();
      });
    }
  }

  @Test
  void duplicateForeignKeyAndGeneratedSuccessConstraintsReturnTheSameMysqlErrors() {
    var o = fixture("PREPARING"); long id = tx().execute(s -> cancellation(true).create(o.order(), "seed"));
    assertThat(sqlError(() -> cancellation(false).create(o.order(), "duplicate")))
        .isEqualTo(sqlError(() -> cancellation(true).create(o.order(), "duplicate")));
    assertThat(sqlError(() -> returning(false).create(Long.MAX_VALUE, "fk")))
        .isEqualTo(sqlError(() -> returning(true).create(Long.MAX_VALUE, "fk")));
    var other = fixture("PREPARING"); long refund = seedRefund(other, "CANCELLATION");
    long second = tx().execute(s -> refund(true).createRetry(refund, 2).refundId());
    assertThat(sqlError(() -> refund(false).createRetry(refund, 2)))
        .isEqualTo(sqlError(() -> refund(true).createRetry(refund, 2)));
    tx().executeWithoutResult(s -> refund(true).complete(refund, "SUCCEEDED", "OK", null));
    assertThat(sqlError(() -> refund(false).complete(second, "SUCCEEDED", "OK", null)))
        .isEqualTo(sqlError(() -> refund(true).complete(second, "SUCCEEDED", "OK", null)));
    assertThat(jdbc.queryForObject("SELECT status FROM refunds WHERE id=?", String.class, second)).isEqualTo("READY");
    assertThat(id).isPositive();
  }

  @Test
  void everyLockStepExistingAndMissingRowFootprintsMatchFrozenAtRepeatableRead() {
    var o = fixture("PREPARING");
    assertThat(cancellationLocks(o, false)).isEqualTo(cancellationLocks(o, true));
    assertThat(returnLocks(o, null, false)).isEqualTo(returnLocks(o, null, true));
    long cancellation = tx().execute(s -> cancellation(true).create(o.order(), "existing"));
    assertThat(cancellationLocks(o, false)).isEqualTo(cancellationLocks(o, true));
    long returned = tx().execute(s -> returning(true).create(o.order(), "existing"));
    assertThat(returnLocks(o, returned, false)).isEqualTo(returnLocks(o, returned, true));
    tx().executeWithoutResult(s -> cancellation(true).createRefund(o.order(), cancellation));
    long refund = jdbc.queryForObject("SELECT id FROM refunds WHERE order_id=?", Long.class, o.order());
    assertThat(refundLocks(refund, false)).isEqualTo(refundLocks(refund, true));
    assertThat(refundLocks(Long.MAX_VALUE, false)).isEqualTo(refundLocks(Long.MAX_VALUE, true));
  }

  @Test
  void nativeReadersMutationsAndGeneratedIdDoNotFlushOrDetachDirtyManagedMember() {
    var o = fixture("PREPARING");
    tx().executeWithoutResult(s -> {
      var member = entities.find(Member.class, o.member());
      var address = new MemberAddress(o.member(), "T12D", "fixture", "fixture", "fixture", "fixture", null, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
      entities.persist(address); member.assignDefaultAddress(address); entities.flush(); member.clearDefaultAddress();
      var c = cancellation(false); c.findOrderForUpdate(o.member(), o.order()); c.findExisting(o.order());
      long id = c.create(o.order(), "pending context"); c.cancelDelivery(c.findDeliveryForUpdate(o.order()).deliveryId()); c.createRefund(o.order(), id);
      var r = returning(false); long returnId = r.create(o.order(), "pending context"); r.findForDecision(returnId); r.decide(returnId, "APPROVED", null, o.admin()); r.receive(returnId, false, o.admin());
      var f = refund(false); long refund = jdbc.queryForObject("SELECT id FROM refunds WHERE order_id=?", Long.class, o.order());
      f.findReadyForUpdate(refund); f.markProcessing(refund); f.complete(refund, "FAILED", null, null); f.createRetry(refund, 2);
      assertThat(jdbc.queryForObject("SELECT default_address_id FROM members WHERE id=?", Long.class, o.member())).isEqualTo(address.getId());
      assertThat(entities.contains(member)).isTrue(); entities.flush();
      assertThat(jdbc.queryForObject("SELECT default_address_id FROM members WHERE id=?", Long.class, o.member())).isNull();
      s.setRollbackOnly();
    });
  }

  private Snapshot cancellationSnapshot(OrderFixture o, boolean frozen) {
    return tx().execute(s -> {
      Map<String,Long> before = counters();
      var result = new CancellationService(cancellation(frozen), manager, inventory).request(o.member(), o.order(), "frozen");
      Map<String,Long> delta = delta(before); long id = result.cancellationId();
      assertThat(id).isPositive().isEqualTo(jdbc.queryForObject("SELECT id FROM order_cancellations WHERE order_id=?", Long.class, o.order()));
      assertThat(jdbc.queryForObject("SELECT cancellation_id FROM refunds WHERE order_id=?", Long.class, o.order())).isEqualTo(id);
      assertThat(jdbc.queryForObject("SELECT requested_at FROM refunds WHERE order_id=?", Timestamp.class, o.order())).isEqualTo(Timestamp.from(NOW));
      var rows = effects(o); rows.add(jdbc.queryForList("SELECT status,reason,requested_at,completed_at FROM order_cancellations WHERE order_id=?", o.order()));
      s.setRollbackOnly(); return new Snapshot(Arrays.asList(result.status(), result.reason(), result.requestedAt(), result.completedAt()), rows, delta);
    });
  }
  private Snapshot returnSnapshot(OrderFixture o, boolean frozen, boolean restock) {
    return tx().execute(s -> {
      Map<String,Long> before = counters();
      var service = new ReturnService(returning(frozen), manager, notifications, audits, inventory, 7, CLOCK);
      var requested = service.request(o.member(), o.order(), "frozen"); service.approve(o.admin(), requested.returnId());
      var result = service.receive(o.admin(), requested.returnId(), restock); Map<String,Long> delta = delta(before);
      assertThat(result.returnId()).isPositive().isEqualTo(jdbc.queryForObject("SELECT return_id FROM refunds WHERE order_id=?", Long.class, o.order()));
      var rows = effects(o); rows.add(jdbc.queryForList("SELECT status,reason,rejection_reason,restock,requested_at,decided_at,received_at,completed_at,decided_by_admin_id,received_by_admin_id FROM order_returns WHERE order_id=?", o.order()));
      rows.add(jdbc.queryForList("SELECT type,reference_type FROM notifications WHERE member_id=? ORDER BY id", o.member()));
      rows.add(jdbc.queryForList("SELECT action,target_type FROM admin_audit_logs WHERE admin_id=? ORDER BY id", o.admin()));
      s.setRollbackOnly(); return new Snapshot(Arrays.asList(result.status(), result.reason(), result.rejectionReason(), result.restock(), result.requestedAt(), result.decidedAt(), result.receivedAt(), result.completedAt()), rows, delta);
    });
  }
  private Snapshot refundSnapshot(OrderFixture o, long id, boolean frozen) {
    return tx().execute(s -> {
      var f = refund(frozen); Map<String,Long> before = counters();
      f.findReadyForUpdate(id); f.markProcessing(id); f.incrementReconciliationAttempts(id, 1); f.complete(id, "FAILED", "REJECTED", "TOSS_REJECTED");
      var original = f.findRetryTarget(id); assertThat(f.findRetry(original.sourceId(), original.source(), 2)).isNull();
      var next = f.createRetry(id, 2); assertThat(next.refundId()).isPositive().isEqualTo(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class));
      assertThat(jdbc.queryForObject("SELECT idempotency_key FROM refunds WHERE id=?", String.class, next.refundId()))
          .startsWith("refund-").isNotEqualTo(jdbc.queryForObject("SELECT idempotency_key FROM refunds WHERE id=?", String.class, id));
      f.markProcessing(next.refundId()); f.complete(next.refundId(), "SUCCEEDED", "DONE", null); f.releaseCoupon(o.order());
      if (original.cancellationId()!=null) f.completeCancellation(original.cancellationId()); else f.completeReturn(original.returnId());
      var view = f.find(next.refundId()); Map<String,Long> delta = delta(before);
      var rows = effects(o); rows.add(jdbc.queryForList("SELECT status,completed_at FROM " + (original.cancellationId()!=null ? "order_cancellations" : "order_returns") + " WHERE order_id=?", o.order()));
      rows.add(jdbc.queryForList("SELECT status,reserved_order_id,used_at FROM member_coupons WHERE member_id=?", o.member()));
      s.setRollbackOnly(); return new Snapshot(Arrays.asList(view.orderId(),view.source(),view.status(),view.amount(),view.attemptNo(),view.reconciliationAttempts(),view.providerStatus(),view.failureCode(),view.requestedAt(),view.processedAt(),view.completedAt()),rows,delta);
    });
  }
  private List<List<Map<String,Object>>> effects(OrderFixture o) {
    var rows = new ArrayList<List<Map<String,Object>>>();
    rows.add(jdbc.queryForList("SELECT status,cancelled_at FROM deliveries WHERE order_id=?", o.order()));
    rows.add(jdbc.queryForList("SELECT sku_id,available_quantity,reserved_quantity,version FROM inventories WHERE sku_id IN (?,?) ORDER BY sku_id", o.firstSku(),o.secondSku()));
    rows.add(jdbc.queryForList("SELECT sku_id,type,quantity,available_before,available_after,reserved_before,reserved_after FROM inventory_movements WHERE sku_id IN (?,?) ORDER BY id", o.firstSku(),o.secondSku()));
    rows.add(jdbc.queryForList("SELECT source,status,amount,attempt_no,reconciliation_attempts,provider_status,failure_code,requested_at,processed_at,completed_at FROM refunds WHERE order_id=? ORDER BY attempt_no", o.order()));
    return rows;
  }
  private long seedRefund(OrderFixture o, String source) {
    return tx().execute(s -> {
      if (source.equals("CANCELLATION")) { long id = cancellation(true).create(o.order(), "seed"); cancellation(true).createRefund(o.order(),id); }
      else {
        long id=returning(true).create(o.order(),"seed");
        returning(true).decide(id,"APPROVED",null,o.admin());
        returning(true).receive(id,false,o.admin());
        returning(true).createRefund(o.order(),id);
      }
      return jdbc.queryForObject("SELECT id FROM refunds WHERE order_id=?", Long.class,o.order());
    });
  }
  private List<List<Map<String,Object>>> cancellationLocks(OrderFixture o, boolean frozen) {
    var c=cancellation(frozen);
    return trace(List.of(() -> c.findOrderForUpdate(o.member(),o.order()), () -> c.findExisting(o.order()), () -> c.findDeliveryForUpdate(o.order()), () -> c.hasSuccessfulPayment(o.order()), () -> c.hasReturnForUpdate(o.order())));
  }
  private List<List<Map<String,Object>>> returnLocks(OrderFixture o, Long id, boolean frozen) {
    var r=returning(frozen); var calls=new ArrayList<Supplier<?>>();
    calls.add(() -> r.findOrderForUpdate(o.member(),o.order())); calls.add(() -> r.findByOrderForUpdate(o.order())); calls.add(() -> r.hasCancellationForUpdate(o.order())); calls.add(() -> r.findDeliveryForUpdate(o.order()));
    calls.add(() -> r.findForDecision(id==null?Long.MAX_VALUE:id)); calls.add(() -> r.findForReceive(id==null?Long.MAX_VALUE:id)); return trace(calls);
  }
  private List<List<Map<String,Object>>> refundLocks(long id, boolean frozen) {
    var r=refund(frozen);
    return trace(List.of(() -> r.findReadyForUpdate(id), () -> r.findRetryTarget(id), () -> r.findRetry(Long.MAX_VALUE,"CANCELLATION",2), () -> r.findForReconciliation(id), () -> r.findForCompletion(id)));
  }
  private List<List<Map<String,Object>>> trace(List<Supplier<?>> calls) {
    return tx().execute(s -> {
      assertThat(jdbc.queryForObject("SELECT @@transaction_isolation",String.class)).isEqualTo("REPEATABLE-READ");
      var result=new ArrayList<List<Map<String,Object>>>();
      for(var call:calls) { call.get(); var locks=jdbc.queryForList("SELECT OBJECT_NAME,INDEX_NAME,LOCK_TYPE,LOCK_MODE,LOCK_DATA FROM performance_schema.data_locks WHERE OBJECT_SCHEMA=DATABASE() AND THREAD_ID=(SELECT THREAD_ID FROM performance_schema.threads WHERE PROCESSLIST_ID=CONNECTION_ID()) ORDER BY OBJECT_NAME,INDEX_NAME,LOCK_TYPE,LOCK_MODE,LOCK_DATA"); assertThat(locks).isNotEmpty(); result.add(locks); }
      s.setRollbackOnly(); return result;
    });
  }
  private Map<String,Long> counters() {
    var result=new LinkedHashMap<String,Long>();
    jdbc.query("SHOW SESSION STATUS WHERE Variable_name IN ('Com_select','Com_insert','Com_insert_select','Com_update','Com_update_multi')", (org.springframework.jdbc.core.RowCallbackHandler)rs -> result.put(rs.getString(1),rs.getLong(2))); return result;
  }
  private Map<String,Long> delta(Map<String,Long> before) { var after=counters(); after.replaceAll((key,value)->value-before.get(key)); return after; }
  private String sqlError(Runnable call) {
    try { tx().executeWithoutResult(s -> call.run()); throw new AssertionError("Expected SQL constraint failure"); }
    catch(RuntimeException error) { for(Throwable cause=error;cause!=null;cause=cause.getCause()) if(cause instanceof SQLException sql)return sql.getSQLState()+":"+sql.getErrorCode(); throw new AssertionError("Expected MySQL error",error); }
  }
  private OrderFixture fixture(String delivery) { return new AfterSalesFixtures(jdbc,entities,manager).paidOrder(delivery,new BigDecimal("12.34")); }
  private CancellationPersistenceAdapter cancellation(boolean frozen) { return frozen?new FrozenCancellationJdbcReference(jdbc,CLOCK):new CancellationPersistenceAdapter(entities,CLOCK); }
  private ReturnPersistenceAdapter returning(boolean frozen) { return frozen?new FrozenReturnJdbcReference(jdbc,CLOCK):new ReturnPersistenceAdapter(entities,CLOCK); }
  private RefundPersistenceAdapter refund(boolean frozen) { return frozen?new FrozenRefundJdbcReference(jdbc,CLOCK):new RefundPersistenceAdapter(entities,CLOCK); }
  private TransactionTemplate tx() { var tx=new TransactionTemplate(manager);tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);return tx; }
  private record Snapshot(List<Object> response,List<List<Map<String,Object>>> rows,Map<String,Long> counters) {}
}
