package com.pawcycle.backend.commerce.billing.application;

import com.pawcycle.backend.commerce.BillingPreparationResponse;
import com.pawcycle.backend.commerce.CommerceException;
import com.pawcycle.backend.commerce.TossBillingAdapter;
import com.pawcycle.backend.commerce.billing.persistence.BillingPersistenceAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BillingApplicationService {
  private static final Logger log = LoggerFactory.getLogger(BillingApplicationService.class);

  private final BillingPersistenceAdapter billing;
  private final TransactionTemplate transaction;
  private final TossBillingAdapter provider;

  public BillingApplicationService(
      BillingPersistenceAdapter billing,
      org.springframework.transaction.PlatformTransactionManager transactionManager,
      TossBillingAdapter provider) {
    this.billing = billing;
    this.transaction = new TransactionTemplate(transactionManager);
    this.provider = provider;
  }

  public BillingPreparationResponse prepare(long memberId) {
    String token = transaction.execute(status -> billing.createPreparation(memberId));
    log.info("Billing method preparation created. memberId={}", memberId);
    return new BillingPreparationResponse(token);
  }

  public void complete(long memberId, String prepareToken, String authKey) {
    if (!provider.isConfigured()) {
      throw new CommerceException(503, "PAYMENT_PROVIDER_UNAVAILABLE", "Toss Billing Provider가 현재 환경에 구성되지 않았습니다.");
    }
    if (authKey == null || authKey.isBlank()) {
      throw new CommerceException(400, "VALIDATION_FAILED", "authKey가 필요합니다.");
    }
    BillingPersistenceAdapter.ClaimedPreparation prepared =
        transaction.execute(status -> billing.claim(memberId, prepareToken));

    // Provider I/O is deliberately outside the persistence transaction.
    String billingKey = provider.issueBillingKey(prepared.customerKey(), authKey).billingKey();
    transaction.executeWithoutResult(status -> billing.register(memberId, prepareToken, billingKey));
    log.info("Billing method registration completed. memberId={}", memberId);
  }
}
