package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import com.pawcycle.backend.commerce.returning.api.ReturnResponse;
import com.pawcycle.backend.commerce.returning.persistence.ReturnPersistenceAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

class ReturnServiceTests {
  @Test
  void createdAndIdempotentReturnUseTheSameProjection() {
    ReturnPersistenceAdapter adapter = mock(ReturnPersistenceAdapter.class);
    PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    TransactionStatus status = mock(TransactionStatus.class);
    when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
    doNothing().when(manager).commit(status);
    when(adapter.findOrderForUpdate(1L, 2L))
        .thenReturn(new ReturnPersistenceAdapter.OrderLock(2L, "PAID"));
    when(adapter.findByOrderForUpdate(2L)).thenReturn(null);
    when(adapter.hasCancellationForUpdate(2L)).thenReturn(false);
    when(adapter.findDeliveryForUpdate(2L))
        .thenReturn(new ReturnPersistenceAdapter.DeliveryView("DELIVERED", java.sql.Timestamp.from(java.time.Instant.now())));
    when(adapter.create(2L, "different")).thenReturn(4L);
    when(adapter.find(4L))
        .thenReturn(new ReturnPersistenceAdapter.ReturnView(4L, "REQUESTED", "different", null, null, java.sql.Timestamp.from(java.time.Instant.now()), null, null, null));
    ReturnService service =
        new ReturnService(
            adapter,
            manager,
            mock(NotificationService.class),
            mock(AdminAuditService.class),
            mock(InventoryService.class),
            7,
            java.time.Clock.systemUTC());

    ReturnResponse response = service.request(1L, 2L, "different");
    assertThat(response.returnId()).isEqualTo(4L);
    assertThat(response.status()).isEqualTo("REQUESTED");
    assertThat(response.reason()).isEqualTo("different");
  }
}
