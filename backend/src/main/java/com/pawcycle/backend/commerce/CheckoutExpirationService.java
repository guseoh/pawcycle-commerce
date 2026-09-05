package com.pawcycle.backend.commerce;

import com.pawcycle.backend.commerce.checkout.persistence.CheckoutExpirationPersistenceAdapter;
import java.util.List;
import org.springframework.stereotype.Service;

/** Selects only expired READY payments; each candidate is committed independently. */
@Service
public class CheckoutExpirationService {
  private final CheckoutExpirationPersistenceAdapter expirations;
  private final CheckoutExpirationProcessor processor;

  public CheckoutExpirationService(
      CheckoutExpirationPersistenceAdapter expirations, CheckoutExpirationProcessor processor) {
    this.expirations = expirations;
    this.processor = processor;
  }

  public int expireDue(int batchSize) {
    if (batchSize < 1) throw new IllegalArgumentException("batchSize must be positive");
    int expired = 0;
    for (Long paymentId : expirations.findDuePaymentIds(batchSize)) {
      if (processor.expire(paymentId)) expired++;
    }
    return expired;
  }
}
