package com.pawcycle.backend.commerce.membership.application;

import com.pawcycle.backend.commerce.membership.persistence.MembershipReevaluationRequestPersistenceAdapter;
import com.pawcycle.backend.commerce.membership.persistence.MembershipReevaluationRequestPersistenceAdapter.DueRequest;
import com.pawcycle.backend.common.logging.RedactedFailure;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Membership re-evaluation outside the money transactions (PAYMENT-MEMBERSHIP-001).
 *
 * <p>Payment success, reconciliation, subscription billing and refunds call {@link #request} inside
 * their own transaction instead of evaluating the grade there. Before this, the evaluation's
 * {@code member_memberships} gap lock could deadlock two first-time buyers, and the deadlock rolled
 * back the whole payment confirmation after the provider had already approved it.
 *
 * <p>The request commits or rolls back with the money change, so it cannot be lost. {@link
 * #processDue} then evaluates each member in its own transaction and deletes the request in that
 * same transaction: a failed evaluation leaves the request for a later run (with back-off), and a
 * request that arrives during the evaluation survives because the delete checks its version. The
 * grade therefore follows the paid orders within one job interval instead of immediately.
 */
@Service
public class MembershipReevaluationService {
  static final String FAILURES = "pawcycle.membership.reevaluation.failures";
  static final Duration FIRST_RETRY = Duration.ofSeconds(10);
  static final Duration MAX_RETRY = Duration.ofHours(1);
  private static final Logger log = LoggerFactory.getLogger(MembershipReevaluationService.class);

  private final MembershipReevaluationRequestPersistenceAdapter requests;
  private final MembershipEvaluationService evaluation;
  private final TransactionTemplate perMember;
  private final Clock clock;
  private final Counter lockFailures;
  private final Counter otherFailures;

  public MembershipReevaluationService(
      MembershipReevaluationRequestPersistenceAdapter requests,
      MembershipEvaluationService evaluation,
      PlatformTransactionManager transactionManager,
      Clock clock,
      MeterRegistry registry) {
    this.requests = requests;
    this.evaluation = evaluation;
    this.perMember = new TransactionTemplate(transactionManager);
    this.clock = clock;
    // Registered up front so the series exist (as 0) before the first failure.
    this.lockFailures = Counter.builder(FAILURES).tag("reason", "lock").register(registry);
    this.otherFailures = Counter.builder(FAILURES).tag("reason", "other").register(registry);
  }

  /**
   * Records that the member's grade must be re-evaluated. Must run inside the transaction that
   * changed the member's paid orders, so the request commits or rolls back with that change.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void request(long memberId) {
    requests.upsert(memberId, clock.instant());
  }

  /** Evaluates up to {@code limit} due members, each in its own transaction; returns the count done. */
  public int processDue(int limit) {
    int evaluated = 0;
    for (DueRequest due : perMember.execute(status -> requests.findDue(clock.instant(), limit))) {
      try {
        perMember.executeWithoutResult(status -> {
          evaluation.evaluate(due.memberId());
          requests.deleteIfUnchanged(due.memberId(), due.version());
        });
        evaluated++;
      } catch (PessimisticLockingFailureException lockFailure) {
        // A concurrent admin re-evaluation of the same member: try again on a later run.
        lockFailures.increment();
        log.warn("Membership re-evaluation deferred by a lock conflict; memberId={}, attempts={}",
            due.memberId(), due.attempts() + 1);
        postpone(due);
      } catch (RuntimeException failure) {
        otherFailures.increment();
        log.error("Membership re-evaluation failed; memberId={}, attempts={}",
            due.memberId(), due.attempts() + 1, RedactedFailure.of(failure));
        postpone(due);
      }
    }
    return evaluated;
  }

  private void postpone(DueRequest due) {
    Instant next = clock.instant().plus(backoff(due.attempts()));
    perMember.executeWithoutResult(status -> requests.postponeIfUnchanged(due.memberId(), due.version(), next));
  }

  /** 10s, 20s, 40s, ... capped at 1 hour, so a request that keeps failing never blocks the others. */
  static Duration backoff(int previousAttempts) {
    if (previousAttempts >= 20) return MAX_RETRY;
    Duration delay = FIRST_RETRY.multipliedBy(1L << previousAttempts);
    return delay.compareTo(MAX_RETRY) > 0 ? MAX_RETRY : delay;
  }
}
