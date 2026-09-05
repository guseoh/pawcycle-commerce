package com.pawcycle.backend.commerce;

import org.springframework.stereotype.Service;

/** Billing retry use-case facade; transactional SQL lives in the retry processor. */
@Service
public class SubscriptionBillingService {
  private final SubscriptionBillingRetryProcessor processor;

  public SubscriptionBillingService(SubscriptionBillingRetryProcessor processor) {
    this.processor = processor;
  }

  public void recordExplicitFailure(long paymentId, String failureCode) {
    processor.recordExplicitFailure(paymentId, failureCode);
  }

  public void recordExplicitFailure(long paymentId, String failureCode, String providerStatus) {
    processor.recordExplicitFailure(paymentId, failureCode, providerStatus);
  }

  public long prepareNextAttempt(long failedPaymentId) {
    return processor.prepareNextAttempt(failedPaymentId);
  }

  public long retryHeldBilling(long failedPaymentId, long adminId) {
    return processor.retryHeldBilling(failedPaymentId, adminId);
  }

  public boolean recordUnknownReconciliationAttempt(long paymentId) {
    return processor.recordUnknownReconciliationAttempt(paymentId);
  }
}
