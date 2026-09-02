package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

class PaymentReconciliationServiceTests {
  @Test
  void failedProcessingBillingUsesExplicitFailureAndRetryWithoutChargingAgain() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    TransactionStatus transactionStatus = mock(TransactionStatus.class);
    TossBillingAdapter billing = mock(TossBillingAdapter.class);
    SubscriptionBillingService failures = mock(SubscriptionBillingService.class);
    when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
    doNothing().when(manager).commit(transactionStatus);
    when(billing.isConfigured()).thenReturn(true);
    when(billing.queryCharge("provider-order"))
        .thenReturn(new TossBillingAdapter.ChargeResult("FAILED", "DECLINED"));

    Map<String, Object> work = new HashMap<>();
    work.put("id", 44L);
    work.put("type", "BILLING");
    work.put("status", "PROCESSING");
    work.put("provider_order_id", "provider-order");
    work.put("reconciliation_attempts", 0);
    Map<String, Object> locked = new HashMap<>();
    locked.put("id", 44L);
    locked.put("order_id", 12L);
    locked.put("type", "BILLING");
    locked.put("status", "PROCESSING");
    locked.put("reconciliation_attempts", 1);
    locked.put("member_id", 2L);
    locked.put("source", "SUBSCRIPTION");
    Map<String, Object> view = Map.of("paymentId", 44L, "status", "FAILED");
    when(jdbc.queryForList(anyString(), any(Object[].class)))
        .thenReturn(List.of(work), List.of(locked), List.of(view));
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

    PaymentReconciliationService service =
        new PaymentReconciliationService(
            jdbc,
            manager,
            mock(TossPaymentAdapter.class),
            billing,
            mock(NotificationService.class),
            mock(MembershipEvaluationService.class),
            mock(InventoryService.class),
            mock(AdminAuditService.class),
            failures);

    assertThat(service.reconcile(44L)).containsEntry("status", "FAILED");
    verify(failures).recordExplicitFailure(44L, "RECONCILED_FAILED", "DECLINED");
    verify(failures).prepareNextAttempt(44L);
    verify(billing, never()).charge(anyString(), anyString(), any());
  }
}
