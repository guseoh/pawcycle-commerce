package com.pawcycle.backend.commerce.membership.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.commerce.audit.application.AdminAuditService;
import com.pawcycle.backend.commerce.membership.application.MembershipEvaluationService;
import com.pawcycle.backend.commerce.membership.application.MembershipAdminApplicationService;
import com.pawcycle.backend.commerce.membership.api.MembershipGradeRequest;
import com.pawcycle.backend.support.SecondaryReadFixtures;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class MembershipEvaluationWriteProtocolIntegrationTests {
  private static final Instant NOW = Instant.parse("2026-10-13T01:02:03.123456Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entities;
  @Autowired PlatformTransactionManager manager;
  @Autowired MembershipAdminApplicationService admin;
  @Autowired AdminAuditService audits;
  @Autowired WebApplicationContext context;
  @Autowired ObjectMapper json;
  private final java.util.Set<Long> ownedMembers = new java.util.HashSet<>();

  @AfterEach
  void cleanCommittedFixtures() {
    tx().executeWithoutResult(s -> {
      for (long member : ownedMembers) {
        jdbc.update("DELETE FROM admin_audit_logs WHERE admin_id=?", member);
        jdbc.update("DELETE FROM member_coupons WHERE member_id=?", member);
        jdbc.update("DELETE FROM membership_histories WHERE member_id=?", member);
        jdbc.update("DELETE FROM member_memberships WHERE member_id=?", member);
        jdbc.update("DELETE FROM orders WHERE member_id=?", member);
        jdbc.update("DELETE FROM members WHERE id=?", member);
      }
      jdbc.update("DELETE FROM membership_grades WHERE code LIKE 'T12C_%'");
      jdbc.update("DELETE FROM coupons WHERE name='T12-C'");
    });
  }
  private long member() { long id = fixtures().member(); ownedMembers.add(id); return id; }

  @Test
  void exact365DayCutoffNullPaidAtStatusSumAndGradeTieMatchFrozenJdbc() {
    rollback(() -> {
      var f = fixtures(); long member = member();
      Instant boundary = NOW.minus(365, ChronoUnit.DAYS);
      f.order(member, "ONE_TIME", "PAID", new BigDecimal("1.11"), Timestamp.from(boundary.minusNanos(1000)));
      f.order(member, "ONE_TIME", "PAID", new BigDecimal("2.22"), Timestamp.from(boundary));
      f.order(member, "ONE_TIME", "PAID", new BigDecimal("3.33"), Timestamp.from(boundary.plusNanos(1000)));
      f.order(member, "ONE_TIME", "PAID", new BigDecimal("4.44"), null);
      f.order(member, "ONE_TIME", "PAYMENT_PENDING", new BigDecimal("5.55"), Timestamp.from(NOW));
      assertThat(actual().memberExists(member)).isEqualTo(legacy().memberExists(member)).isTrue();
      assertThat(actual().memberExists(Long.MAX_VALUE)).isFalse();
      assertThat(actual().paidAmountLastYear(member)).isEqualTo(legacy().paidAmountLastYear(member))
          .isEqualByComparingTo("5.55");
      assertThat(actual().paidAmountLastYear(member())).isEqualTo(legacy().paidAmountLastYear(Long.MAX_VALUE));
      jdbc.update("UPDATE membership_grades SET active=false");
      long first = grade("5.55", null); long second = grade("5.55", null);
      assertThat(second).isGreaterThan(first);
      assertThat(actual().findGrade(new BigDecimal("5.55")).gradeId()).isEqualTo(legacy().findGrade(new BigDecimal("5.55")).gradeId()).isEqualTo(second);
      assertThat(actual().findGrade(new BigDecimal("5.55")).benefitCouponId()).isNull();
      assertThat(actual().findGrade(BigDecimal.ZERO)).isNull();
    });
  }

  @Test
  void insertUpdateTwoLocksHistoryCouponAndSameGradeReplayMatchFrozen() {
    rollback(() -> {
      long member = member(); long coupon = coupon(); long grade = grade("0", coupon);
      long selects = counter("Com_select"); long inserts = counter("Com_insert");
      assertThat(actual().findMembershipForUpdate(member)).isNull();
      actual().saveMembership(member, grade, new BigDecimal("19.99"));
      assertThat(counter("Com_select") - selects).isEqualTo(2);
      assertThat(counter("Com_insert") - inserts).isEqualTo(1);
      actual().recordChange(member, null, grade, new BigDecimal("19.99"), coupon);
      var actualState = state(member);
      jdbc.update("DELETE FROM member_coupons WHERE member_id=?", member);
      jdbc.update("DELETE FROM membership_histories WHERE member_id=?", member);
      jdbc.update("DELETE FROM member_memberships WHERE member_id=?", member);
      selects = counter("Com_select"); inserts = counter("Com_insert");
      assertThat(legacy().findMembershipForUpdate(member)).isNull();
      legacy().saveMembership(member, grade, new BigDecimal("19.99"));
      assertThat(counter("Com_select") - selects).isEqualTo(2);
      assertThat(counter("Com_insert") - inserts).isEqualTo(1);
      legacy().recordChange(member, null, grade, new BigDecimal("19.99"), coupon);
      assertThat(state(member)).isEqualTo(actualState);
      jdbc.update("UPDATE membership_grades SET active=false WHERE id<>?", grade);
      new MembershipEvaluationService(actual()).evaluate(member);
      new MembershipEvaluationService(actual()).evaluate(member);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM membership_histories WHERE member_id=?", Long.class, member)).isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member_coupons WHERE member_id=?", Long.class, member)).isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT evaluated_at FROM member_memberships WHERE member_id=?", Timestamp.class, member)).isEqualTo(Timestamp.from(NOW));
    });
  }

  @Test
  void couponAndAuditFailureRollBackMembershipHistoryAndGrade() {
    long member = tx().execute(s -> member());
    long grade = tx().execute(s -> grade("0", null));

    assertRollbackParity(member,
        () -> {
          actual().saveMembership(member, grade, BigDecimal.TEN);
          actual().recordChange(member, null, grade, BigDecimal.TEN, Long.MAX_VALUE);
        },
        () -> {
          legacy().saveMembership(member, grade, BigDecimal.TEN);
          legacy().recordChange(member, null, grade, BigDecimal.TEN, Long.MAX_VALUE);
        });

    assertRollbackParity(member,
        () -> {
          actual().saveMembership(member, grade, BigDecimal.TEN);
          actual().recordChange(member, Long.MAX_VALUE, grade, BigDecimal.TEN, null);
        },
        () -> {
          legacy().saveMembership(member, grade, BigDecimal.TEN);
          legacy().recordChange(member, Long.MAX_VALUE, grade, BigDecimal.TEN, null);
        });

    String actualCode = code();
    int actualError = errorCode(() -> admin.createGrade(
        Long.MAX_VALUE, new MembershipGradeRequest(actualCode, "audit rollback", BigDecimal.ZERO, 0, true, null)));
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM membership_grades WHERE code=?", Long.class, actualCode)).isZero();
    String frozenCode = code();
    int frozenError = errorCode(() -> {
      long createdGrade = new LegacyMembershipPersistenceAdapter(jdbc, CLOCK).createGrade(
          new MembershipGradeRequest(frozenCode, "audit rollback", BigDecimal.ZERO, 0, true, null));
      audits.append(Long.MAX_VALUE, "MEMBERSHIP_GRADE_CREATE", "MEMBERSHIP_GRADE", createdGrade);
    });
    assertThat(frozenError).isEqualTo(actualError).isEqualTo(1452);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM membership_grades WHERE code=?", Long.class, frozenCode)).isZero();
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM admin_audit_logs WHERE admin_id=?", Long.class, Long.MAX_VALUE)).isZero();

    assertRollbackParity(member,
        () -> actual().saveMembership(member, Long.MAX_VALUE, BigDecimal.TEN),
        () -> legacy().saveMembership(member, Long.MAX_VALUE, BigDecimal.TEN));
  }

  @Test
  void generatedIdentityNullableCouponDecimalDatabaseLimitAndFailuresMatchFrozen() {
    rollback(() -> {
      var request = new MembershipGradeRequest(code(), "identity", new BigDecimal("9999999999.99"), 9, true, null);
      long selects = counter("Com_select"), inserts = counter("Com_insert");
      long id = new MembershipPersistenceAdapter(entities).createGrade(command(request));
      assertThat(counter("Com_select") - selects).isZero();
      assertThat(counter("Com_insert") - inserts).isEqualTo(1);
      assertThat(id).isPositive();
      assertThat(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).isEqualTo(id);
      assertThat(jdbc.queryForObject("SELECT minimum_purchase_amount FROM membership_grades WHERE id=?", BigDecimal.class, id))
          .isEqualTo(request.minimumPurchaseAmount());
      assertThat(jdbc.queryForObject("SELECT benefit_coupon_id FROM membership_grades WHERE id=?", Long.class, id)).isNull();
      assertThat(jdbc.queryForObject("SELECT numeric_precision FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='membership_grades' AND column_name='minimum_purchase_amount'", Integer.class)).isEqualTo(12);
      selects = counter("Com_select"); inserts = counter("Com_insert");
      long frozenId = new LegacyMembershipPersistenceAdapter(jdbc, CLOCK).createGrade(
          new MembershipGradeRequest(code(), request.name(), request.minimumPurchaseAmount(), 9, true, null));
      assertThat(counter("Com_select") - selects).isEqualTo(1);
      assertThat(counter("Com_insert") - inserts).isEqualTo(1);
      assertThat(frozenId).isGreaterThan(id);
    });
    for (String amount : List.of("10000000000.00", "-1.00")) {
      assertThat(errorCode(() -> new MembershipPersistenceAdapter(entities).createGrade(command(new MembershipGradeRequest(code(), "bad amount", new BigDecimal(amount), 0, true, null)))))
          .isEqualTo(errorCode(() -> new LegacyMembershipPersistenceAdapter(jdbc, CLOCK).createGrade(new MembershipGradeRequest(code(), "bad amount", new BigDecimal(amount), 0, true, null))));
    }
    assertThat(errorCode(() -> new MembershipPersistenceAdapter(entities).createGrade(command(new MembershipGradeRequest(code(), "bad fk", BigDecimal.ZERO, 0, true, Long.MAX_VALUE)))))
        .isEqualTo(errorCode(() -> new LegacyMembershipPersistenceAdapter(jdbc, CLOCK).createGrade(new MembershipGradeRequest(code(), "bad fk", BigDecimal.ZERO, 0, true, Long.MAX_VALUE))));
    String duplicate = code(); tx().executeWithoutResult(s -> new MembershipPersistenceAdapter(entities).createGrade(command(new MembershipGradeRequest(duplicate, "duplicate", BigDecimal.ZERO, 0, true, null))));
    assertThat(errorCode(() -> new MembershipPersistenceAdapter(entities).createGrade(command(new MembershipGradeRequest(duplicate, "duplicate", BigDecimal.ZERO, 0, true, null)))))
        .isEqualTo(errorCode(() -> new LegacyMembershipPersistenceAdapter(jdbc, CLOCK).createGrade(new MembershipGradeRequest(duplicate, "duplicate", BigDecimal.ZERO, 0, true, null)))).isEqualTo(1062);
  }

  @Test
  void httpCreatedLocationAndAuditUseTheGeneratedIdAndDuplicateAddsNoAudit() throws Exception {
    long member = tx().execute(s -> member());
    var principal = new UsernamePasswordAuthenticationToken(new AuthenticatedMemberPrincipal(member), null,
        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    var http = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    var request = new MembershipGradeRequest(code(), "HTTP grade", new BigDecimal("123.456"), 2, true, null);
    String body = json.writeValueAsString(request);
    var result = http.perform(post("/api/admin/membership-grades").with(authentication(principal)).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn();
    long id = json.readTree(result.getResponse().getContentAsString()).get("gradeId").asLong();
    assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/admin/membership-grades/" + id);
    assertThat(jdbc.queryForObject("SELECT minimum_purchase_amount FROM membership_grades WHERE id=?", BigDecimal.class, id)).isEqualByComparingTo("123.46");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE admin_id=? AND action='MEMBERSHIP_GRADE_CREATE' AND target_id=?", Long.class, member, id)).isEqualTo(1);
    assertThatThrownBy(() -> admin.createGrade(member, request)).isInstanceOf(RuntimeException.class);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE admin_id=? AND action='MEMBERSHIP_GRADE_CREATE'", Long.class, member)).isEqualTo(1);
    rollback(() -> {
      var frozen = new LegacyMembershipPersistenceAdapter(jdbc, CLOCK);
      long legacyId = frozen.createGrade(new MembershipGradeRequest(code(), "round", request.minimumPurchaseAmount(), 2, true, null));
      assertThat(jdbc.queryForObject("SELECT minimum_purchase_amount FROM membership_grades WHERE id=?", BigDecimal.class, legacyId)).isEqualByComparingTo("123.46");
    });
  }

  @Test
  void nativeReadsWritesAndIdentityInsertDoNotAutoFlushDirtyManagedGrades() {
    rollback(() -> {
      long member = member(); long id = grade("0", null);
      var dirty = entities.find(MembershipGradeEntity.class, id); dirty.active = false;
      actual().memberExists(member); actual().paidAmountLastYear(member); actual().findGrade(BigDecimal.ZERO);
      actual().saveMembership(member, id, BigDecimal.ZERO); actual().recordChange(member, null, id, BigDecimal.ZERO, null);
      new MembershipPersistenceAdapter(entities).createGrade(new CreateMembershipGradeCommand(code(), "identity", BigDecimal.ONE, 0, true, null));
      assertThat(jdbc.queryForObject("SELECT active FROM membership_grades WHERE id=?", Boolean.class, id)).isTrue();
      entities.flush();
      assertThat(jdbc.queryForObject("SELECT active FROM membership_grades WHERE id=?", Boolean.class, id)).isFalse();
    });
  }

  @Test
  void physicalExistingAndMissingRowLocksMatchFrozenAtRepeatableRead() {
    long lower = tx().execute(s -> member());
    long missing = tx().execute(s -> member());
    long upper = tx().execute(s -> member());
    long grade = tx().execute(s -> grade("0", null));
    tx().executeWithoutResult(s -> { actual().saveMembership(lower, grade, BigDecimal.ZERO); actual().saveMembership(upper, grade, BigDecimal.ZERO); });
    for (long member : List.of(lower, missing, upper)) {
      assertThat(locks(member, false)).isNotEmpty().isEqualTo(locks(member, true));
    }
  }

  @Test
  void competingMissingRowInsertsRetainOneDeadlockAndOneWinner() throws Exception {
    for (boolean frozen : List.of(false, true)) {
      long member = tx().execute(s -> member()); long grade = tx().execute(s -> grade("0", null));
      CountDownLatch locked = new CountDownLatch(2);
      try (var executor = Executors.newFixedThreadPool(2)) {
        var a = executor.submit(() -> initialInsert(member, grade, frozen, locked));
        var b = executor.submit(() -> initialInsert(member, grade, frozen, locked));
        assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(0, 1213);
      }
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member_memberships WHERE member_id=?", Long.class, member)).isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM membership_histories WHERE member_id=?", Long.class, member)).isEqualTo(1);
    }
  }

  @Test
  void existingRowLockBlocksCompetingUpdateOnTheSamePhysicalRecord() throws Exception {
    long member = tx().execute(s -> member()); long grade = tx().execute(s -> grade("0", null));
    tx().executeWithoutResult(s -> actual().saveMembership(member, grade, BigDecimal.ZERO));
    for (boolean frozen : List.of(false, true)) {
      try (var executor = Executors.newSingleThreadExecutor()) {
        var connection = new java.util.concurrent.atomic.AtomicLong(); var ready = new CountDownLatch(1);
        var future = tx().execute(s -> {
          if (frozen) legacy().findMembershipForUpdate(member); else actual().findMembershipForUpdate(member);
          var contender = executor.submit(() -> tx().execute(status -> {
            connection.set(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class)); ready.countDown();
            return jdbc.update("UPDATE member_memberships SET evaluated_purchase_amount=1 WHERE member_id=?", member);
          }));
          await(ready);
          long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10); long waits = 0;
          while (System.nanoTime() < deadline) {
            waits = jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks requested ON requested.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID JOIN performance_schema.data_locks held ON held.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID WHERE requested.OBJECT_NAME='member_memberships' AND requested.INDEX_NAME='PRIMARY' AND requested.THREAD_ID=(SELECT THREAD_ID FROM performance_schema.threads WHERE PROCESSLIST_ID=?) AND held.THREAD_ID=(SELECT THREAD_ID FROM performance_schema.threads WHERE PROCESSLIST_ID=CONNECTION_ID())", Long.class, connection.get());
            if (waits > 0) break;
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
          }
          assertThat(waits).as("Contender must wait for the holder's physical PK record lock").isPositive();
          assertThat(contender.isDone()).isFalse(); return contender;
        });
        assertThat(future.get(20, TimeUnit.SECONDS)).isEqualTo(1);
      }
    }
  }

  private int initialInsert(long member, long grade, boolean frozen, CountDownLatch locked) {
    try {
      tx().executeWithoutResult(s -> {
        if (frozen) assertThat(legacy().findMembershipForUpdate(member)).isNull();
        else assertThat(actual().findMembershipForUpdate(member)).isNull();
        locked.countDown(); await(locked);
        if (frozen) legacy().saveMembership(member, grade, BigDecimal.ZERO);
        else actual().saveMembership(member, grade, BigDecimal.ZERO);
        if (frozen) legacy().recordChange(member, null, grade, BigDecimal.ZERO, null);
        else actual().recordChange(member, null, grade, BigDecimal.ZERO, null);
      }); return 0;
    } catch (RuntimeException error) { return sqlCode(error); }
  }
  private List<String> locks(long member, boolean frozen) {
    return tx().execute(s -> {
      assertThat(jdbc.queryForObject("SELECT @@transaction_isolation", String.class)).isEqualTo("REPEATABLE-READ");
      if (frozen) legacy().findMembershipForUpdate(member); else actual().findMembershipForUpdate(member);
      return jdbc.queryForList("SELECT CONCAT_WS('|',LOCK_TYPE,COALESCE(INDEX_NAME,''),LOCK_MODE,COALESCE(LOCK_DATA,'')) FROM performance_schema.data_locks WHERE OBJECT_SCHEMA=DATABASE() AND OBJECT_NAME='member_memberships' AND THREAD_ID=(SELECT THREAD_ID FROM performance_schema.threads WHERE PROCESSLIST_ID=CONNECTION_ID()) ORDER BY LOCK_TYPE,INDEX_NAME,LOCK_MODE,LOCK_DATA", String.class);
    });
  }
  private void assertRollbackParity(long member, Runnable currentWrite, Runnable frozenWrite) {
    int currentError = errorCode(currentWrite);
    assertThat(state(member)).allSatisfy(rows -> assertThat(rows).isEmpty());
    int frozenError = errorCode(frozenWrite);
    assertThat(frozenError).isEqualTo(currentError).isEqualTo(1452);
    assertThat(state(member)).allSatisfy(rows -> assertThat(rows).isEmpty());
  }

  private int errorCode(Runnable call) {
    try { tx().executeWithoutResult(s -> call.run()); throw new AssertionError("Expected DB constraint failure"); }
    catch (RuntimeException error) { return sqlCode(error); }
  }
  private static int sqlCode(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) if (cause instanceof SQLException sql) return sql.getErrorCode();
    throw new AssertionError("Expected MySQL error", error);
  }
  private static void await(CountDownLatch latch) {
    try { assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue(); }
    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
  }
  private TransactionTemplate tx() { var tx = new TransactionTemplate(manager); tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ); return tx; }
  private void rollback(Runnable call) { tx().executeWithoutResult(s -> { call.run(); s.setRollbackOnly(); }); }
  private MembershipEvaluationPersistenceAdapter actual() { return new MembershipEvaluationPersistenceAdapter(entities, CLOCK); }
  private LegacyMembershipEvaluationPersistenceAdapter legacy() { return new LegacyMembershipEvaluationPersistenceAdapter(jdbc, CLOCK); }
  private SecondaryReadFixtures fixtures() { return new SecondaryReadFixtures(jdbc, entities); }
  private long counter(String name) { return jdbc.queryForObject("SHOW SESSION STATUS LIKE ?", (rs, row) -> rs.getLong(2), name); }
  private static String code() { return "T12C_" + UUID.randomUUID().toString().substring(0, 12); }
  private long grade(String amount, Long coupon) {
    jdbc.update("INSERT INTO membership_grades(code,name,minimum_purchase_amount,display_order,active,benefit_coupon_id) VALUES (?,'T12-C',?,0,true,?)", code(), new BigDecimal(amount), coupon);
    return fixtures().lastId();
  }
  private long coupon() {
    jdbc.update("INSERT INTO coupons(name,discount_type,discount_value,minimum_order_amount,valid_from,valid_until,active) VALUES ('T12-C','FIXED_AMOUNT',1,0,'2026-01-01','2027-01-01',true)"); return fixtures().lastId();
  }
  private List<List<java.util.Map<String,Object>>> state(long member) {
    return List.of(jdbc.queryForList("SELECT grade_id,evaluated_purchase_amount,evaluated_at FROM member_memberships WHERE member_id=?", member),
        jdbc.queryForList("SELECT from_grade_id,to_grade_id,evaluated_purchase_amount,changed_at FROM membership_histories WHERE member_id=? ORDER BY id", member),
        jdbc.queryForList("SELECT coupon_id,status,issued_at FROM member_coupons WHERE member_id=? ORDER BY id", member));
  }
  private static CreateMembershipGradeCommand command(MembershipGradeRequest r) {
    return new CreateMembershipGradeCommand(r.code(), r.name(), r.minimumPurchaseAmount(), r.displayOrder(), r.active(), r.benefitCouponId());
  }
}
