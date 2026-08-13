package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;

class SubscriptionBillingProcessorTests {
	@Test
	void successfulSubscriptionBillingReevaluatesMembership() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
		org.springframework.transaction.TransactionStatus status = mock(org.springframework.transaction.TransactionStatus.class);
		CommerceService commerce = mock(CommerceService.class);
		when(manager.getTransaction(any(org.springframework.transaction.TransactionDefinition.class))).thenReturn(status);
		org.mockito.Mockito.doNothing().when(manager).commit(status);
		when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(
				List.of(Map.of("id", 8L, "order_id", 9L, "member_id", 10L, "schedule_id", 11L)), List.of());
		when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
		SubscriptionBillingProcessor processor = new SubscriptionBillingProcessor(jdbc, manager, mock(TossBillingAdapter.class), mock(SubscriptionBillingService.class), mock(PaymentReconciliationService.class), mock(DeliveryService.class), mock(NotificationService.class), commerce);

		processor.completeSuccess(8L, "DONE");

		verify(commerce).evaluateMembership(10L);
	}

	@Test
	void processingBillingIsReconciledWithoutChargingAgain() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		TossBillingAdapter provider = mock(TossBillingAdapter.class);
		SubscriptionBillingService retries = mock(SubscriptionBillingService.class);
		PaymentReconciliationService reconciliation = mock(PaymentReconciliationService.class);
		when(provider.isConfigured()).thenReturn(true);
		when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Map<String,Object>>>any()))
				.thenReturn(List.of(Map.of("id", 42L, "status", "PROCESSING")));

		SubscriptionBillingProcessor processor = new SubscriptionBillingProcessor(
				jdbc, mock(PlatformTransactionManager.class), provider, retries, reconciliation, mock(DeliveryService.class), mock(NotificationService.class), mock(CommerceService.class));

		assertThat(processor.processReadyPayments()).isEqualTo(1);
		verify(reconciliation).reconcile(42L);
		verify(provider, never()).charge(anyString(), anyString(), any());
	}
}
