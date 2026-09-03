package com.pawcycle.backend.commerce;

import org.springframework.stereotype.Service;

@Service
public class BillingMethodQueryService {
  private final BillingPaymentMethodRepository billingPaymentMethods;
  private final TossPaymentAdapter provider;

  public BillingMethodQueryService(
      BillingPaymentMethodRepository billingPaymentMethods, TossPaymentAdapter provider) {
    this.billingPaymentMethods = billingPaymentMethods;
    this.provider = provider;
  }

  public BillingMethodResponse active(long memberId) {
    long count = billingPaymentMethods.countByMemberIdAndStatus(memberId, "ACTIVE");
    return new BillingMethodResponse("TOSS", provider.isConfigured(), count > 0);
  }
}
