package com.pawcycle.backend.subscription;

import org.springframework.stereotype.Service;

/** Idempotency cleanup use-case facade; SQL batch work lives in the cleanup processor. */
@Service
public class SubscriptionIdempotencyCleanupService {
  private final SubscriptionIdempotencyCleanupProcessor processor;

  public SubscriptionIdempotencyCleanupService(SubscriptionIdempotencyCleanupProcessor processor) {
    this.processor = processor;
  }

  public SubscriptionIdempotencyCleanupResult deleteExpired(int batchSize) {
    return processor.deleteExpired(batchSize);
  }
}
