package com.pawcycle.backend.commerce.payment.application;

import com.pawcycle.backend.common.external.ExternalCall;
import com.pawcycle.backend.common.external.ExternalCallFailures;
import com.pawcycle.backend.common.logging.BusinessEvent;
import com.pawcycle.backend.commerce.audit.application.AdminAuditService;
import com.pawcycle.backend.commerce.inventory.application.InventoryService;
import com.pawcycle.backend.commerce.payment.persistence.PaymentReconciliationPersistenceAdapter;
import com.pawcycle.backend.commerce.payment.persistence.PaymentReconciliationPersistenceAdapter.FailureCandidate;
import com.pawcycle.backend.commerce.payment.persistence.PaymentReconciliationPersistenceAdapter.FailureEvidence;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * #417 Phase 2b: failure confirmation of an isolated NORMAL/PROCESSING payment (PWC-0134).
 *
 * <p>Only the provider's ABORTED or EXPIRED for the same payment (same order id, payment key and
 * amount, classified by {@link PaymentReconciliationService#queryOnly}) can lead to a write. DONE
 * stops and points to the success command; every other observation stops and keeps PROCESSING and
 * the reservation.
 *
 * <p>{@link #inspect} never writes. {@link #confirm} may be called only after the operator has
 * blocked new requests on every instance that can process the payment and confirmed that started
 * work has ended; the command enforces that attestation. The write locks the payment row, re-checks
 * identity, state, age and the RESERVE ledger, then commits in one transaction: RELEASE per SKU ->
 * payment FAILED -> order PAYMENT_PENDING -> PAYMENT_FAILED -> reserved coupon AVAILABLE -> audit.
 * The cart is kept and no customer notification is created, as in the normal failure path.
 *
 * <p>If the write transaction ends with an exception, the commit may still have happened (lost
 * acknowledgement). The outcome is then decided by reading payment, order, ledger, coupon and audit
 * together in a new transaction.
 */
@Service
public class ProcessingPaymentFailureConfirmationService {
  static final Duration MINIMUM_AGE_AFTER_EXPIRY = Duration.ofMinutes(10);
  public static final String FAILURE_CODE = "RECOVERED_PROVIDER_FAILED";
  static final Set<String> CONFIRMABLE_PROVIDER_STATUSES = Set.of("ABORTED", "EXPIRED");
  static final List<String> AUDIT_ACTIONS =
      List.of("PAYMENT_PROCESSING_FAILED_ABORTED", "PAYMENT_PROCESSING_FAILED_EXPIRED");
  private static final Logger log = LoggerFactory.getLogger(ProcessingPaymentFailureConfirmationService.class);

  private final PaymentReconciliationPersistenceAdapter payments;
  private final PaymentReconciliationService reconciliation;
  private final InventoryService inventory;
  private final AdminAuditService audits;
  private final TossPaymentAdapter provider;
  private final TransactionTemplate transaction;
  private final TransactionTemplate verification;
  private final Clock clock;
  private final ExternalCallFailures failures;

  public ProcessingPaymentFailureConfirmationService(
      PaymentReconciliationPersistenceAdapter payments,
      PaymentReconciliationService reconciliation,
      InventoryService inventory,
      AdminAuditService audits,
      TossPaymentAdapter provider,
      PlatformTransactionManager transactionManager,
      Clock clock,
      ExternalCallFailures failures) {
    this.payments = payments;
    this.reconciliation = reconciliation;
    this.inventory = inventory;
    this.audits = audits;
    this.provider = provider;
    this.transaction = new TransactionTemplate(transactionManager);
    this.verification = new TransactionTemplate(transactionManager);
    this.verification.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.verification.setReadOnly(true);
    this.clock = clock;
    this.failures = failures;
  }

  /** Read-only: eligibility and provider query, never a write. */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public Outcome inspect(long paymentId, long operatorMemberId) {
    return finished(paymentId, operatorMemberId, run(paymentId, operatorMemberId, false));
  }

  /** Writes only for the same payment's ABORTED/EXPIRED; the caller must have fenced the workers. */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public Outcome confirm(long paymentId, long operatorMemberId) {
    return finished(paymentId, operatorMemberId, run(paymentId, operatorMemberId, true));
  }

  private Outcome finished(long paymentId, long operatorMemberId, Result result) {
    BusinessEvent event = BusinessEvent.of(BusinessEvent.Name.PAYMENT_FAILURE_CONFIRMATION_FINISHED)
        .with(BusinessEvent.Field.PAYMENT_ID, paymentId)
        .with(BusinessEvent.Field.ADMIN_ID, operatorMemberId)
        .with(BusinessEvent.Field.OUTCOME, result.outcome().name());
    if (result.providerStatus() != null) event.with(BusinessEvent.Field.PROVIDER_STATUS, result.providerStatus());
    event.log();
    return result.outcome();
  }

  private Result run(long paymentId, long operatorMemberId, boolean write) {
    if (!Boolean.TRUE.equals(transaction.execute(status -> payments.isAdmin(operatorMemberId)))) {
      return new Result(Outcome.REFUSED_OPERATOR_NOT_ADMIN, null);
    }
    Timestamp expiredBefore = Timestamp.from(clock.instant().minus(MINIMUM_AGE_AFTER_EXPIRY));
    FailureCandidate before =
        transaction.execute(status -> payments.findFailureCandidate(paymentId, expiredBefore, false));
    if (before != null && "FAILED".equals(before.status())
        && confirmed(transaction.execute(status -> evidence(paymentId))))
      return new Result(Outcome.ALREADY_CONFIRMED, null);
    Outcome refusal = eligibility(before);
    if (refusal != null) return new Result(refusal, null);

    if (!provider.isConfigured()) return new Result(Outcome.REFUSED_PROVIDER_UNAVAILABLE, null);
    PaymentReconciliationService.ProviderObservation observation;
    try {
      observation = reconciliation.queryOnly(paymentId);
    } catch (RuntimeException unavailable) {
      failures.record(ExternalCall.TOSS_PAYMENT_QUERY, unavailable, "paymentId=" + paymentId);
      return new Result(Outcome.STOPPED_PROVIDER_UNKNOWN, null);
    }
    String providerStatus = observation.providerStatus();
    if ("SUCCEEDED".equals(observation.status()))
      return new Result(Outcome.STOPPED_PROVIDER_DONE, providerStatus);
    if (!"FAILED".equals(observation.status()) || !CONFIRMABLE_PROVIDER_STATUSES.contains(providerStatus))
      return new Result(
          "UNKNOWN".equals(observation.status()) ? Outcome.STOPPED_PROVIDER_UNKNOWN : Outcome.STOPPED_PROVIDER_OTHER,
          null);
    if (!write) return new Result(Outcome.INSPECTED_CONFIRMABLE, providerStatus);

    try {
      return new Result(
          transaction.execute(status -> confirmLocked(paymentId, operatorMemberId, expiredBefore, before, providerStatus)),
          providerStatus);
    } catch (RuntimeException failure) {
      log.warn("Payment failure confirmation did not report a commit; re-reading paymentId={} type={}",
          paymentId, failure.getClass().getSimpleName(), failure);
      return new Result(afterUncertainCommit(paymentId), providerStatus);
    }
  }

  private Outcome confirmLocked(
      long paymentId, long operatorMemberId, Timestamp expiredBefore, FailureCandidate before, String providerStatus) {
    FailureCandidate locked = payments.findFailureCandidate(paymentId, expiredBefore, true);
    if (locked != null && "FAILED".equals(locked.status()) && confirmed(evidence(paymentId)))
      return Outcome.ALREADY_CONFIRMED;
    if (eligibility(locked) != null || !sameIdentity(before, locked)) return Outcome.CHANGED_DURING_QUERY;

    for (PaymentReconciliationPersistenceAdapter.OrderItem item : payments.findOrderItems(locked.orderId())) {
      inventory.release(item.skuId(), item.quantity(), paymentId);
    }
    if (payments.markProcessingFailed(paymentId, providerStatus, FAILURE_CODE) != 1)
      throw new IllegalStateException("payment left PROCESSING under its own row lock");
    if (payments.markPendingOrderPaymentFailed(locked.orderId()) != 1)
      throw new IllegalStateException("order is no longer PAYMENT_PENDING");
    payments.releaseReservedCoupon(locked.orderId());
    audits.append(operatorMemberId, auditAction(providerStatus), "PAYMENT", paymentId);
    return Outcome.FAILURE_CONFIRMED;
  }

  /** New transaction and connection: was the failed write committed after all? */
  private Outcome afterUncertainCommit(long paymentId) {
    FailureEvidence evidence;
    try {
      evidence = verification.execute(status -> evidence(paymentId));
    } catch (RuntimeException unreadable) {
      log.warn("Payment failure confirmation outcome unreadable paymentId={} type={}",
          paymentId, unreadable.getClass().getSimpleName());
      return Outcome.UNDETERMINED;
    }
    if (confirmed(evidence)) return Outcome.CONFIRMED_AFTER_COMMIT_ERROR;
    if (untouched(evidence)) return Outcome.FAILED_ROLLED_BACK;
    return Outcome.UNDETERMINED;
  }

  private FailureEvidence evidence(long paymentId) {
    return payments.findFailureEvidence(paymentId, FAILURE_CODE, AUDIT_ACTIONS);
  }

  /** Every effect of a committed failure confirmation is present. */
  static boolean confirmed(FailureEvidence e) {
    return e != null
        && "FAILED".equals(e.status())
        && FAILURE_CODE.equals(e.failureCode())
        && CONFIRMABLE_PROVIDER_STATUSES.contains(e.providerStatus())
        && "PAYMENT_FAILED".equals(e.orderStatus())
        && e.orderItemCount() > 0
        && e.releaseCount().equals(e.orderItemCount())
        && e.releaseMismatchCount() == 0L
        && e.reservedCouponCount() == 0L
        && e.auditCount() == 1L;
  }

  /** None of the effects is present: the write rolled back as a whole. */
  static boolean untouched(FailureEvidence e) {
    return e != null
        && "PROCESSING".equals(e.status())
        && "PAYMENT_PENDING".equals(e.orderStatus())
        && e.releaseCount() == 0L
        && e.auditCount() == 0L;
  }

  private static Outcome eligibility(FailureCandidate candidate) {
    if (candidate == null) return Outcome.REFUSED_NOT_FOUND;
    if (!"NORMAL".equals(candidate.type())) return Outcome.REFUSED_NOT_NORMAL;
    if (!"PROCESSING".equals(candidate.status())) return Outcome.REFUSED_NOT_PROCESSING;
    if (!"PAYMENT_PENDING".equals(candidate.orderStatus())) return Outcome.REFUSED_ORDER_NOT_PENDING;
    if (blank(candidate.providerOrderId()) || blank(candidate.paymentKey()) || candidate.amount() == null)
      return Outcome.REFUSED_IDENTITY_MISSING;
    if (candidate.expiredLongEnough() == null || candidate.expiredLongEnough() != 1L) return Outcome.REFUSED_TOO_RECENT;
    if (candidate.succeededPaymentCount() != 0L) return Outcome.REFUSED_ORDER_HAS_SUCCEEDED_PAYMENT;
    if (candidate.settledCount() != 0L
        || candidate.orderItemCount() == 0L
        || !candidate.reserveCount().equals(candidate.orderItemCount())
        || candidate.reserveMismatchCount() != 0L)
      return Outcome.REFUSED_RESERVATION_MISMATCH;
    return null;
  }

  private static boolean sameIdentity(FailureCandidate before, FailureCandidate locked) {
    return before.orderId().equals(locked.orderId())
        && before.providerOrderId().equals(locked.providerOrderId())
        && before.paymentKey().equals(locked.paymentKey())
        && before.amount().compareTo(locked.amount()) == 0;
  }

  private static String auditAction(String providerStatus) {
    return "ABORTED".equals(providerStatus) ? AUDIT_ACTIONS.get(0) : AUDIT_ACTIONS.get(1);
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private record Result(Outcome outcome, String providerStatus) {}

  public enum Outcome {
    FAILURE_CONFIRMED,
    ALREADY_CONFIRMED,
    CONFIRMED_AFTER_COMMIT_ERROR,
    INSPECTED_CONFIRMABLE,
    CHANGED_DURING_QUERY,
    STOPPED_PROVIDER_DONE,
    STOPPED_PROVIDER_UNKNOWN,
    STOPPED_PROVIDER_OTHER,
    REFUSED_OPERATOR_NOT_ADMIN,
    REFUSED_PROVIDER_UNAVAILABLE,
    REFUSED_NOT_FOUND,
    REFUSED_NOT_NORMAL,
    REFUSED_NOT_PROCESSING,
    REFUSED_ORDER_NOT_PENDING,
    REFUSED_IDENTITY_MISSING,
    REFUSED_TOO_RECENT,
    REFUSED_ORDER_HAS_SUCCEEDED_PAYMENT,
    REFUSED_RESERVATION_MISMATCH,
    FAILED_ROLLED_BACK,
    UNDETERMINED;

    /** The requested operation reached its intended end state. */
    public boolean completed() {
      return this == FAILURE_CONFIRMED
          || this == ALREADY_CONFIRMED
          || this == CONFIRMED_AFTER_COMMIT_ERROR
          || this == INSPECTED_CONFIRMABLE;
    }

    /** The outcome of a write is unknown or the write failed; state must be re-read before retrying. */
    public boolean failed() {
      return this == FAILED_ROLLED_BACK || this == UNDETERMINED;
    }
  }
}
