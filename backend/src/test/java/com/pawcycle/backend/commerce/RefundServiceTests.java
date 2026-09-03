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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

class RefundServiceTests {
  @Test
  void zeroAmountRefundCompletesLocallyWithoutProviderWrite() {
    NativeQueryExecutor jdbc = mock(NativeQueryExecutor.class);
    PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    TransactionStatus transactionStatus = mock(TransactionStatus.class);
    TossRefundAdapter provider = mock(TossRefundAdapter.class);
    when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
    doNothing().when(manager).commit(transactionStatus);
    Map<String, Object> work = new HashMap<>();
    work.put("id", 7L);
    work.put("status", "READY");
    work.put("idempotency_key", "zero-key");
    work.put("amount", BigDecimal.ZERO);
    Map<String, Object> completion = new HashMap<>();
    completion.put("id", 7L);
    completion.put("order_id", 11L);
    completion.put("source", "CANCELLATION");
    completion.put("cancellation_id", 3L);
    completion.put("return_id", null);
    completion.put("status", "PROCESSING");
    completion.put("reconciliation_attempts", 0);
    completion.put("member_id", 4L);
    Map<String, Object> view = Map.of("refundId", 7L, "status", "SUCCEEDED");
    when(jdbc.queryForList(anyString(), any(Object[].class)))
        .thenReturn(List.of(work), List.of(completion), List.of(view));
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

    RefundService service =
        new RefundService(
            jdbc,
            manager,
            provider,
            mock(NotificationService.class),
            mock(MembershipEvaluationService.class),
            mock(AdminAuditService.class),
            mock(CommerceMetrics.class),
            java.time.Clock.systemUTC());

    assertThat(service.process(7L)).containsEntry("status", "SUCCEEDED");
    verify(provider, never()).refund(anyString(), any());
  }

  @Test
  void processingReconcileQueriesProviderWithoutIssuingAnotherRefund() {
    NativeQueryExecutor jdbc = mock(NativeQueryExecutor.class);
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

    Map<String, Object> row = new HashMap<>();
    row.put("id", 91L);
    row.put("status", "PROCESSING");
    row.put("idempotency_key", "refund-key");
    row.put("reconciliation_attempts", 0);
    row.put("order_id", 11L);
    row.put("source", "RETURN");
    row.put("return_id", 12L);
    row.put("cancellation_id", null);
    row.put("member_id", 4L);
    when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

    RefundService service =
        new RefundService(
            jdbc,
            manager,
            provider,
            mock(NotificationService.class),
            mock(MembershipEvaluationService.class),
            mock(AdminAuditService.class),
            metrics,
            java.time.Clock.systemUTC());

    Map<String, Object> result = service.reconcile(91L);

    assertThat(result).isNotNull();
    verify(provider).reconcile("refund-key");
    verify(provider, never()).refund(anyString(), any());
  }
}
