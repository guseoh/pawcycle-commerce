package com.pawcycle.backend.commerce.membership.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.pawcycle.backend.support.SecondaryReadFixtures;
import jakarta.persistence.EntityManager;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** PAYMENT-MEMBERSHIP-001 request/job protocol on real MySQL. */
@SpringBootTest
@ActiveProfiles("test")
class MembershipReevaluationIntegrationTests {
  @Autowired MembershipReevaluationService reevaluation;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entities;
  @Autowired PlatformTransactionManager manager;
  @MockitoSpyBean MembershipEvaluationService evaluation;
  private final Set<Long> members = new HashSet<>();

  @AfterEach
  void cleanUp() {
    reset(evaluation);
    new TransactionTemplate(manager).executeWithoutResult(status -> {
      for (long member : members) {
        jdbc.update("DELETE FROM membership_reevaluation_requests WHERE member_id=?", member);
        jdbc.update("DELETE FROM member_coupons WHERE member_id=?", member);
        jdbc.update("DELETE FROM membership_histories WHERE member_id=?", member);
        jdbc.update("DELETE FROM member_memberships WHERE member_id=?", member);
        jdbc.update("DELETE FROM members WHERE id=?", member);
      }
    });
  }

  @Test
  void requestOutsideATransactionIsRefused() {
    long member = member();
    assertThatThrownBy(() -> reevaluation.request(member)).isInstanceOf(IllegalTransactionStateException.class);
    assertThat(request(member)).isNull();
  }

  @Test
  void requestCommitsAndRollsBackWithTheCallerTransaction() {
    long member = member();
    new TransactionTemplate(manager).executeWithoutResult(status -> {
      reevaluation.request(member);
      status.setRollbackOnly();
    });
    assertThat(request(member)).isNull();

    inTransaction(() -> reevaluation.request(member));
    assertThat(request(member)).containsEntry("request_version", 1L).containsEntry("attempts", 0);
  }

  @Test
  void repeatedRequestBumpsTheVersionAndMakesItDueAgain() {
    long member = member();
    inTransaction(() -> reevaluation.request(member));
    // Times are compared in SQL against UTC_TIMESTAMP: the service binds them as UTC (Hibernate
    // jdbc.time_zone), while JdbcTemplate would use the JVM default zone.
    jdbc.update("UPDATE membership_reevaluation_requests SET attempts=3,"
        + "next_attempt_at=UTC_TIMESTAMP(6) + INTERVAL 1 HOUR WHERE member_id=?", member);

    inTransaction(() -> reevaluation.request(member));

    assertThat(request(member)).containsEntry("request_version", 2L).containsEntry("attempts", 0);
    assertThat(dueWithin(member, "UTC_TIMESTAMP(6) + INTERVAL 1 SECOND")).isTrue();
  }

  @Test
  void dueRequestIsEvaluatedAndRemovedInOneTransaction() {
    long member = member();
    inTransaction(() -> reevaluation.request(member));

    reevaluation.processDue(1000);

    verify(evaluation).evaluate(member);
    assertThat(request(member)).isNull();
    assertThat(count("SELECT COUNT(*) FROM member_memberships WHERE member_id=?", member)).isEqualTo(1);
  }

  @Test
  void requestArrivingDuringTheEvaluationIsKeptForTheNextRun() {
    long member = member();
    inTransaction(() -> reevaluation.request(member));
    // A payment of the same member commits while the job is evaluating (separate transaction).
    doAnswer(call -> {
      call.callRealMethod();
      TransactionTemplate other = new TransactionTemplate(manager);
      other.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
      other.executeWithoutResult(status -> reevaluation.request(member));
      return null;
    }).when(evaluation).evaluate(member);

    reevaluation.processDue(1000);

    assertThat(request(member)).containsEntry("request_version", 2L);
    assertThat(count("SELECT COUNT(*) FROM member_memberships WHERE member_id=?", member)).isEqualTo(1);
  }

  @Test
  void failedEvaluationKeepsTheRequestAndBacksOff() {
    long member = member();
    inTransaction(() -> reevaluation.request(member));
    doThrow(new IllegalStateException("grade missing")).when(evaluation).evaluate(member);

    reevaluation.processDue(1000);
    reevaluation.processDue(1000); // not due again yet

    verify(evaluation, times(1)).evaluate(member);
    assertThat(request(member)).containsEntry("attempts", 1).containsEntry("request_version", 1L);
    assertThat(dueWithin(member, "UTC_TIMESTAMP(6) + INTERVAL 5 SECOND")).as("backed off ~10s").isFalse();
    assertThat(count("SELECT COUNT(*) FROM member_memberships WHERE member_id=?", member)).isZero();
  }

  @Test
  void lockFailureIsDeferredLikeAnyOtherFailure() {
    long member = member();
    inTransaction(() -> reevaluation.request(member));
    doThrow(new CannotAcquireLockException("deadlock")).when(evaluation).evaluate(anyLong());

    reevaluation.processDue(1000);

    assertThat(request(member)).containsEntry("attempts", 1);
  }

  private long member() {
    long id = new TransactionTemplate(manager).execute(status -> new SecondaryReadFixtures(jdbc, entities).member());
    members.add(id);
    return id;
  }

  private void inTransaction(Runnable work) {
    new TransactionTemplate(manager).executeWithoutResult(status -> work.run());
  }

  private Map<String, Object> request(long member) {
    var rows = jdbc.queryForList(
        "SELECT request_version,attempts,next_attempt_at FROM membership_reevaluation_requests WHERE member_id=?", member);
    return rows.isEmpty() ? null : rows.get(0);
  }

  private boolean dueWithin(long member, String bound) {
    return Boolean.TRUE.equals(jdbc.queryForObject(
        "SELECT next_attempt_at <= " + bound + " FROM membership_reevaluation_requests WHERE member_id=?",
        Boolean.class, member));
  }

  private int count(String sql, long member) {
    return jdbc.queryForObject(sql, Integer.class, member);
  }
}
