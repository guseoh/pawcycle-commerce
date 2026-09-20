package com.pawcycle.backend.commerce.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.commerce.BillingPreparationResponse;
import com.pawcycle.backend.commerce.TossBillingAdapter;
import com.pawcycle.backend.commerce.billing.persistence.BillingPersistenceAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(OutputCaptureExtension.class)
class BillingApplicationServiceLoggingTests {

  private static final long MEMBER_ID_SENTINEL = 918_273_645L;
  private static final String PREPARE_TOKEN_SENTINEL = "sentinel-prepare-token";
  private static final String AUTH_KEY_SENTINEL = "sentinel-auth-key";
  private static final String CUSTOMER_KEY_SENTINEL = "sentinel-customer-key";
  private static final String BILLING_KEY_SENTINEL = "sentinel-billing-key";

  @Test
  void successfulBillingEventsDoNotLogIdentifiersOrCredentials(CapturedOutput output) {
    BillingPersistenceAdapter billing = mock(BillingPersistenceAdapter.class);
    TossBillingAdapter provider = mock(TossBillingAdapter.class);
    PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    TransactionStatus transactionStatus = mock(TransactionStatus.class);
    when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    when(billing.createPreparation(MEMBER_ID_SENTINEL)).thenReturn(PREPARE_TOKEN_SENTINEL);
    when(provider.isConfigured()).thenReturn(true);
    when(billing.claim(MEMBER_ID_SENTINEL, PREPARE_TOKEN_SENTINEL))
        .thenReturn(new BillingPersistenceAdapter.ClaimedPreparation(CUSTOMER_KEY_SENTINEL));
    when(provider.issueBillingKey(CUSTOMER_KEY_SENTINEL, AUTH_KEY_SENTINEL))
        .thenReturn(new TossBillingAdapter.BillingKeyResult(BILLING_KEY_SENTINEL));
    BillingApplicationService service =
        new BillingApplicationService(billing, transactionManager, provider);

    BillingPreparationResponse preparation = service.prepare(MEMBER_ID_SENTINEL);
    service.complete(MEMBER_ID_SENTINEL, PREPARE_TOKEN_SENTINEL, AUTH_KEY_SENTINEL);

    assertThat(preparation.prepareToken()).isEqualTo(PREPARE_TOKEN_SENTINEL);
    verify(billing)
        .register(MEMBER_ID_SENTINEL, PREPARE_TOKEN_SENTINEL, BILLING_KEY_SENTINEL);
    assertThat(output)
        .contains("Billing method preparation created", "Billing method registration completed")
        .doesNotContain(
            Long.toString(MEMBER_ID_SENTINEL),
            PREPARE_TOKEN_SENTINEL,
            AUTH_KEY_SENTINEL,
            CUSTOMER_KEY_SENTINEL,
            BILLING_KEY_SENTINEL);
  }
}
