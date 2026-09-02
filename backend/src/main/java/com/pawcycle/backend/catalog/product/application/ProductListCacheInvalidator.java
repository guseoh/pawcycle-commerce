package com.pawcycle.backend.catalog.product.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class ProductListCacheInvalidator {
  private final ProductListCache productListCache;

  public void invalidateAfterCommit() {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      if (!TransactionSynchronizationManager.isSynchronizationActive()) {
        throw new IllegalStateException("active transaction has no synchronization");
      }
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              productListCache.invalidate();
            }
          });
      return;
    }
    productListCache.invalidate();
  }
}
