package com.pawcycle.backend.catalog.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ProductListCacheInvalidatorTests {
  @AfterEach
  void clearTransactionState() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
    TransactionSynchronizationManager.setActualTransactionActive(false);
  }

  @Test
  void committedTransactionInvalidatesCacheAfterCommit() {
    ProductListCache cache = mock(ProductListCache.class);
    ProductListCacheInvalidator invalidator = new ProductListCacheInvalidator(cache);
    beginTransactionSynchronization();

    invalidator.invalidateAfterCommit();
    verifyNoInteractions(cache);

    List<TransactionSynchronization> synchronizations =
        TransactionSynchronizationManager.getSynchronizations();
    assertThat(synchronizations).hasSize(1);
    synchronizations.getFirst().afterCommit();

    verify(cache).invalidate();
  }

  @Test
  void rolledBackTransactionDoesNotInvalidateCache() {
    ProductListCache cache = mock(ProductListCache.class);
    ProductListCacheInvalidator invalidator = new ProductListCacheInvalidator(cache);
    beginTransactionSynchronization();

    invalidator.invalidateAfterCommit();
    for (TransactionSynchronization synchronization :
        TransactionSynchronizationManager.getSynchronizations()) {
      synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
    }

    verifyNoInteractions(cache);
  }

  private void beginTransactionSynchronization() {
    TransactionSynchronizationManager.setActualTransactionActive(true);
    TransactionSynchronizationManager.initSynchronization();
  }
}
