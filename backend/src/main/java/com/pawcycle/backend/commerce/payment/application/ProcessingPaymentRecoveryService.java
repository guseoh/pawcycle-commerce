package com.pawcycle.backend.commerce.payment.application;

import com.pawcycle.backend.commerce.audit.application.AdminAuditService;
import com.pawcycle.backend.commerce.payment.persistence.PaymentReconciliationPersistenceAdapter;
import com.pawcycle.backend.commerce.payment.persistence.PaymentReconciliationPersistenceAdapter.RecoveryCandidate;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * #417 Phase 2a: success-only local finalization of an isolated NORMAL/PROCESSING payment.
 *
 * <p>Only a provider observation of the same payment's DONE (same order id, payment key and
 * amount, classified by {@link PaymentReconciliationService#queryOnly}) leads to a write. Every
 * other observation stops without changing payment, order, reservation, cart or coupon. Failure
 * finalization and reservation release are intentionally out of scope.
 *
 * <p>Exactly-once does not depend on an operator fence: the write re-locks the payment row and
 * applies effects only while it is still PROCESSING, the same gate normal finalization uses.
 */
@Service
public class ProcessingPaymentRecoveryService {
  static final Duration MINIMUM_AGE_AFTER_EXPIRY = Duration.ofMinutes(10);
  static final String AUDIT_ACTION = "PAYMENT_PROCESSING_RECOVERED";

  private final PaymentReconciliationPersistenceAdapter payments;
  private final PaymentReconciliationService reconciliation;
  private final PaymentApplicationService normalPayments;
  private final AdminAuditService audits;
  private final TossPaymentAdapter provider;
  private final TransactionTemplate transaction;
  private final Clock clock;

  public ProcessingPaymentRecoveryService(
      PaymentReconciliationPersistenceAdapter payments,
      PaymentReconciliationService reconciliation,
      PaymentApplicationService normalPayments,
      AdminAuditService audits,
      TossPaymentAdapter provider,
      PlatformTransactionManager transactionManager,
      Clock clock) {
    this.payments = payments;
    this.reconciliation = reconciliation;
    this.normalPayments = normalPayments;
    this.audits = audits;
    this.provider = provider;
    this.transaction = new TransactionTemplate(transactionManager);
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public Outcome recover(long paymentId, long operatorMemberId) {
    if (!Boolean.TRUE.equals(transaction.execute(status -> payments.isAdmin(operatorMemberId)))) {
      return Outcome.REFUSED_OPERATOR_NOT_ADMIN;
    }
    Timestamp expiredBefore = Timestamp.from(clock.instant().minus(MINIMUM_AGE_AFTER_EXPIRY));
    RecoveryCandidate before =
        transaction.execute(status -> payments.findRecoveryCandidate(paymentId, expiredBefore, false));
    Outcome refusal = eligibility(before);
    if (refusal != null) return refusal;

    if (!reconciliationProviderConfigured()) return Outcome.REFUSED_PROVIDER_UNAVAILABLE;
    PaymentReconciliationService.ProviderObservation observation;
    try {
      observation = reconciliation.queryOnly(paymentId);
    } catch (RuntimeException unavailable) {
      return Outcome.STOPPED_PROVIDER_UNKNOWN;
    }
    if (!"SUCCEEDED".equals(observation.status())) return Outcome.stopped(observation.status());

    return transaction.execute(
        status -> {
          RecoveryCandidate locked = payments.findRecoveryCandidate(paymentId, expiredBefore, true);
          if (locked != null && "SUCCEEDED".equals(locked.status())) return Outcome.ALREADY_FINALIZED;
          Outcome changed = eligibility(locked);
          if (changed != null) return Outcome.CHANGED_DURING_QUERY;
          if (!sameIdentity(before, locked)) return Outcome.CHANGED_DURING_QUERY;
          normalPayments.applyNormalSuccess(paymentId, locked.orderId(), locked.paymentKey());
          audits.append(operatorMemberId, AUDIT_ACTION, "PAYMENT", paymentId);
          return Outcome.RECOVERED;
        });
  }

  private boolean reconciliationProviderConfigured() {
    return provider.isConfigured();
  }

  private static Outcome eligibility(RecoveryCandidate candidate) {
    if (candidate == null) return Outcome.REFUSED_NOT_FOUND;
    if (!"NORMAL".equals(candidate.type())) return Outcome.REFUSED_NOT_NORMAL;
    if (!"PROCESSING".equals(candidate.status())) return Outcome.REFUSED_NOT_PROCESSING;
    if (!"PAYMENT_PENDING".equals(candidate.orderStatus())) return Outcome.REFUSED_ORDER_NOT_PENDING;
    if (blank(candidate.providerOrderId()) || blank(candidate.paymentKey()) || candidate.amount() == null)
      return Outcome.REFUSED_IDENTITY_MISSING;
    if (candidate.expiredLongEnough() == null || candidate.expiredLongEnough() != 1L) return Outcome.REFUSED_TOO_RECENT;
    if (candidate.settledCount() != 0L
        || candidate.reserveCount() == 0L
        || !candidate.reserveCount().equals(candidate.orderItemCount()))
      return Outcome.REFUSED_RESERVATION_MISMATCH;
    return null;
  }

  private static boolean sameIdentity(RecoveryCandidate before, RecoveryCandidate locked) {
    return before.orderId().equals(locked.orderId())
        && before.providerOrderId().equals(locked.providerOrderId())
        && before.paymentKey().equals(locked.paymentKey())
        && before.amount().compareTo(locked.amount()) == 0;
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  public enum Outcome {
    RECOVERED,
    ALREADY_FINALIZED,
    CHANGED_DURING_QUERY,
    STOPPED_PROVIDER_UNKNOWN,
    STOPPED_PROVIDER_FAILED,
    STOPPED_PROVIDER_OTHER,
    REFUSED_OPERATOR_NOT_ADMIN,
    REFUSED_PROVIDER_UNAVAILABLE,
    REFUSED_NOT_FOUND,
    REFUSED_NOT_NORMAL,
    REFUSED_NOT_PROCESSING,
    REFUSED_ORDER_NOT_PENDING,
    REFUSED_IDENTITY_MISSING,
    REFUSED_TOO_RECENT,
    REFUSED_RESERVATION_MISMATCH;

    static Outcome stopped(String observedStatus) {
      if ("UNKNOWN".equals(observedStatus)) return STOPPED_PROVIDER_UNKNOWN;
      if ("FAILED".equals(observedStatus)) return STOPPED_PROVIDER_FAILED;
      return STOPPED_PROVIDER_OTHER;
    }

    public boolean changedState() {
      return this == RECOVERED;
    }
  }
}
