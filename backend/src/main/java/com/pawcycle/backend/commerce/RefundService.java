package com.pawcycle.backend.commerce;

import com.pawcycle.backend.commerce.refund.api.RefundResponse;
import com.pawcycle.backend.commerce.refund.persistence.RefundPersistenceAdapter;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Two-transaction refund executor: provider I/O is always outside the database transaction. */
@Service
public class RefundService {
  private final RefundPersistenceAdapter refunds;
  private final TransactionTemplate transaction;
  private final TossRefundAdapter provider;
  private final NotificationService notifications;
  private final MembershipEvaluationService membershipEvaluation;
  private final AdminAuditService audits;
  private final CommerceMetrics metrics;

  public RefundService(
      RefundPersistenceAdapter refunds,
      org.springframework.transaction.PlatformTransactionManager transactionManager,
      TossRefundAdapter provider,
      NotificationService notifications,
      MembershipEvaluationService membershipEvaluation,
      AdminAuditService audits,
      CommerceMetrics metrics) {
    this.refunds = refunds;
    this.transaction = new TransactionTemplate(transactionManager);
    this.provider = provider;
    this.notifications = notifications;
    this.membershipEvaluation = membershipEvaluation;
    this.audits = audits;
    this.metrics = metrics;
  }

  public RefundResponse process(long id) {
    return process(id, null);
  }

  public RefundResponse process(long id, Long adminId) {
    RefundPersistenceAdapter.RefundWork work =
        transaction.execute(
            status -> {
              RefundPersistenceAdapter.RefundWork row = refunds.findReadyForUpdate(id);
              if (row == null) throw notFound();
              if (!"READY".equals(row.status())) {
                throw new CommerceException(409, "REFUND_STATE_CONFLICT", "준비된 환불만 처리할 수 있습니다.");
              }
              refunds.markProcessing(id);
              return row;
            });
    TossRefundAdapter.RefundResult result;
    if (work.amount().signum() == 0) {
      result = new TossRefundAdapter.RefundResult("SUCCEEDED", "ZERO_AMOUNT");
    } else {
      if (!provider.isConfigured()) throw unavailable();
      Timer.Sample sample = metrics.timer();
      try {
        result = provider.refund(work.idempotencyKey(), work.amount());
      } catch (RuntimeException exception) {
        result = new TossRefundAdapter.RefundResult("UNKNOWN", "NO_RESPONSE");
      } finally {
        metrics.stop(sample, "refund.provider");
      }
    }
    return complete(id, result, adminId, "REFUND_PROCESS");
  }

  public RefundResponse retry(long id) {
    return retry(id, null);
  }

  public RefundResponse retry(long id, Long adminId) {
    return transaction.execute(
        status -> {
          RefundPersistenceAdapter.RetryTarget row = refunds.findRetryTarget(id);
          if (row == null) throw notFound();
          int attempt = row.attemptNo() + 1;
          if (!"FAILED".equals(row.status()) || attempt > 3 || row.sourceId() == null) {
            throw new CommerceException(409, "REFUND_RETRY_NOT_ALLOWED", "환불 재시도를 만들 수 없습니다.");
          }
          RefundPersistenceAdapter.RetryView existing =
              refunds.findRetry(row.sourceId(), row.source(), attempt);
          if (existing != null) return response(existing, row.orderId());
          RefundPersistenceAdapter.RetryView created = refunds.createRetry(id, attempt);
          if (adminId != null) audits.append(adminId, "REFUND_RETRY", "REFUND", created.refundId());
          return response(created, row.orderId());
        });
  }

  public RefundResponse reconcile(long id) {
    return reconcile(id, null);
  }

  public RefundResponse reconcile(long id, Long adminId) {
    if (!provider.isConfigured()) throw unavailable();
    RefundPersistenceAdapter.ReconciliationWork work =
        transaction.execute(
            status -> {
              RefundPersistenceAdapter.ReconciliationWork row = refunds.findForReconciliation(id);
              if (row == null) throw notFound();
              if (!"UNKNOWN".equals(row.status()) && !"PROCESSING".equals(row.status())) {
                throw new CommerceException(409, "REFUND_RECONCILIATION_NOT_ALLOWED", "UNKNOWN 또는 처리 중 환불만 대사할 수 있습니다.");
              }
              if (row.attempts() >= 10) {
                throw new CommerceException(409, "REFUND_RECONCILIATION_EXHAUSTED", "환불 대사 횟수를 초과했습니다.");
              }
              refunds.incrementReconciliationAttempts(id, row.attempts() + 1);
              return row;
            });
    TossRefundAdapter.RefundResult result;
    Timer.Sample sample = metrics.timer();
    try {
      result = provider.reconcile(work.idempotencyKey());
    } catch (RuntimeException exception) {
      result = new TossRefundAdapter.RefundResult("UNKNOWN", "NO_RESPONSE");
    } finally {
      metrics.stop(sample, "refund.provider");
    }
    return complete(id, result, adminId, "REFUND_RECONCILE");
  }

  private RefundResponse complete(
      long id, TossRefundAdapter.RefundResult result, Long adminId, String auditAction) {
    return transaction.execute(
        status -> {
          RefundPersistenceAdapter.CompletionTarget row = refunds.findForCompletion(id);
          if (row == null) throw notFound();
          if (!"PROCESSING".equals(row.status()) && !"UNKNOWN".equals(row.status())) {
            return response(refunds.find(id));
          }
          String state = result.status();
          if (!"SUCCEEDED".equals(state) && !"FAILED".equals(state) && !"UNKNOWN".equals(state)) {
            state = "UNKNOWN";
          }
          if ("UNKNOWN".equals(state)) state = row.status();
          refunds.complete(id, state, result.providerStatus(), "FAILED".equals(state) ? "TOSS_REJECTED" : null);
          if ("SUCCEEDED".equals(state)) {
            refunds.releaseCoupon(row.orderId());
            if ("CANCELLATION".equals(row.source()) && row.cancellationId() != null) {
              refunds.completeCancellation(row.cancellationId());
            } else if (row.returnId() != null) {
              refunds.completeReturn(row.returnId());
            }
            membershipEvaluation.evaluate(row.memberId());
            notifications.create(
                row.memberId(),
                "CANCELLATION".equals(row.source()) ? "CANCELLATION_COMPLETED" : "RETURN_COMPLETED",
                "REFUND",
                id);
          } else if ("UNKNOWN".equals(state)) {
            notifications.create(row.memberId(), "REFUND_ACTION_REQUIRED", "REFUND", id);
          }
          metrics.count("refund", state);
          if (adminId != null) audits.append(adminId, auditAction, "REFUND", id);
          return response(refunds.find(id));
        });
  }

  private static RefundResponse response(RefundPersistenceAdapter.RefundView view) {
    return new RefundResponse(
        view.refundId(),
        view.orderId(),
        view.source(),
        view.status(),
        view.amount(),
        view.attemptNo(),
        view.reconciliationAttempts(),
        view.providerStatus(),
        view.failureCode(),
        view.requestedAt(),
        view.processedAt(),
        view.completedAt());
  }

  private static RefundResponse response(RefundPersistenceAdapter.RetryView view, long orderId) {
    return new RefundResponse(view.refundId(), orderId, null, view.status(), null, view.attemptNo(), 0, null, null, null, null, null);
  }

  private static CommerceException unavailable() {
    return new CommerceException(503, "REFUND_PROVIDER_UNAVAILABLE", "Toss 환불 Provider가 현재 환경에 구성되지 않았습니다.");
  }

  private static CommerceException notFound() {
    return new CommerceException(404, "REFUND_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
  }
}
