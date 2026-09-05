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
import com.pawcycle.backend.commerce.payment.persistence.PaymentReconciliationPersistenceAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

class PaymentReconciliationServiceTests {
  @Test
  void failedProcessingBillingUsesExplicitFailureAndRetryWithoutChargingAgain() {
    PaymentReconciliationPersistenceAdapter adapter = mock(PaymentReconciliationPersistenceAdapter.class);
    PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    TransactionStatus transactionStatus = mock(TransactionStatus.class);
    TossBillingAdapter billing = mock(TossBillingAdapter.class);
    SubscriptionBillingService failures = mock(SubscriptionBillingService.class);
    when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
    doNothing().when(manager).commit(transactionStatus);
    when(billing.isConfigured()).thenReturn(true);
    when(billing.queryCharge("provider-order"))
        .thenReturn(new TossBillingAdapter.ChargeResult("FAILED", "DECLINED"));

    when(adapter.findForStart(44L))
        .thenReturn(new PaymentReconciliationPersistenceAdapter.ReconciliationWork(44L, "BILLING", "PROCESSING", "provider-order", 0));
    when(adapter.findForCompletion(44L))
        .thenReturn(new PaymentReconciliationPersistenceAdapter.ReconciliationTarget(44L, 12L, "BILLING", "PROCESSING", 1, 2L, "SUBSCRIPTION"));
    when(adapter.find(44L))
        .thenReturn(new PaymentReconciliationPersistenceAdapter.PaymentReconciliationView(44L, 12L, "FAILED", 1, null));

    PaymentReconciliationService service =
        new PaymentReconciliationService(
            adapter,
            manager,
            mock(TossPaymentAdapter.class),
            billing,
            mock(NotificationService.class),
            mock(MembershipEvaluationService.class),
            mock(InventoryService.class),
            mock(AdminAuditService.class),
            failures,
            mock(DeliveryService.class),
            java.time.Clock.systemUTC());

    assertThat(service.reconcile(44L).status()).isEqualTo("FAILED");
    verify(failures).recordExplicitFailure(44L, "RECONCILED_FAILED", "DECLINED");
    verify(failures).prepareNextAttempt(44L);
    verify(billing, never()).charge(anyString(), anyString(), any());
  }
}
