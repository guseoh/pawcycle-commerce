package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import com.pawcycle.backend.commerce.refund.persistence.RefundPersistenceAdapter;
import com.pawcycle.backend.commerce.refund.api.RefundResponse;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

class RefundServiceTests {
  @Test
  void zeroAmountRefundCompletesLocallyWithoutProviderWrite() {
    RefundPersistenceAdapter adapter = mock(RefundPersistenceAdapter.class);
    PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    TransactionStatus transactionStatus = mock(TransactionStatus.class);
    TossRefundAdapter provider = mock(TossRefundAdapter.class);
    when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
    doNothing().when(manager).commit(transactionStatus);
    when(adapter.findReadyForUpdate(7L))
        .thenReturn(new RefundPersistenceAdapter.RefundWork(7L, "READY", "zero-key", BigDecimal.ZERO));
    when(adapter.findForCompletion(7L))
        .thenReturn(new RefundPersistenceAdapter.CompletionTarget(7L, 11L, "CANCELLATION", 3L, null, "PROCESSING", 4L));
    when(adapter.find(7L))
        .thenReturn(new RefundPersistenceAdapter.RefundView(7L, 11L, "CANCELLATION", "SUCCEEDED", BigDecimal.ZERO, 1, 0, null, null, null, null, null));

    RefundService service =
        new RefundService(
            adapter,
            manager,
            provider,
            mock(NotificationService.class),
            mock(MembershipEvaluationService.class),
            mock(AdminAuditService.class),
            mock(CommerceMetrics.class));

    assertThat(service.process(7L).status()).isEqualTo("SUCCEEDED");
    verify(provider, never()).refund(anyString(), any());
  }

  @Test
  void processingReconcileQueriesProviderWithoutIssuingAnotherRefund() {
    RefundPersistenceAdapter adapter = mock(RefundPersistenceAdapter.class);
    PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    TransactionStatus transactionStatus = mock(TransactionStatus.class);
    TossRefundAdapter provider = mock(TossRefundAdapter.class);
    CommerceMetrics metrics = mock(CommerceMetrics.class);
    Timer.Sample sample = mock(Timer.Sample.class);
    when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
    doNothing().when(manager).commit(transactionStatus);
    when(provider.isConfigured()).thenReturn(true);
    when(provider.reconcile("refund-key"))
        .thenReturn(new TossRefundAdapter.RefundResult("UNKNOWN", "NO_RESPONSE"));
    when(metrics.timer()).thenReturn(sample);

    when(adapter.findForReconciliation(91L))
        .thenReturn(new RefundPersistenceAdapter.ReconciliationWork(91L, "PROCESSING", "refund-key", 0));
    when(adapter.findForCompletion(91L))
        .thenReturn(new RefundPersistenceAdapter.CompletionTarget(91L, 11L, "RETURN", null, 12L, "PROCESSING", 4L));
    when(adapter.find(91L))
        .thenReturn(new RefundPersistenceAdapter.RefundView(91L, 11L, "RETURN", "PROCESSING", BigDecimal.ONE, 1, 1, "NO_RESPONSE", null, null, null, null));

    RefundService service =
        new RefundService(
            adapter,
            manager,
            provider,
            mock(NotificationService.class),
            mock(MembershipEvaluationService.class),
            mock(AdminAuditService.class),
            metrics);

    RefundResponse result = service.reconcile(91L);

    assertThat(result).isNotNull();
    verify(provider).reconcile("refund-key");
    verify(provider, never()).refund(anyString(), any());
  }
}
