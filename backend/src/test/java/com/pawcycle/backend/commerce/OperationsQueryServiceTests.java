package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class OperationsQueryServiceTests {
	@Test
	void exposesApprovedOperationsWithOnlyExecutableActions() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		Timestamp now = Timestamp.from(Instant.now());
		List<Map<String,Object>> rows = List.of(
				row("DELIVERY_PREPARING", 1L, now, null),
				row("DELIVERY_SHIPPED", 2L, now, null),
				row("DELIVERY_FAILED", 3L, now, null),
				row("RETURN_APPROVED", 4L, now, null),
				row("REFUND_PROCESSING", 5L, now, 1),
				row("PAYMENT_PROCESSING", 6L, now, null),
				row("PAYMENT_RETRY_STOCK_UNAVAILABLE", 7L, now, 1),
				row("PAYMENT_UNKNOWN", 8L, now, null),
				row("REFUND_UNKNOWN", 9L, now, 1));
		when(jdbc.queryForList(anyString())).thenReturn(rows);

		List<Map<String,Object>> result = new OperationsQueryService(jdbc).pending();

		assertThat(result).extracting(row -> row.get("availableActions")).containsExactly(
				List.of("SHIP_DELIVERY"),
				List.of("COMPLETE_DELIVERY", "FAIL_DELIVERY"),
				List.of("RESHIP_DELIVERY"),
				List.of("RECEIVE_RETURN"),
				List.of("RECONCILE_REFUND"),
				List.of("RECONCILE_PAYMENT"),
				List.of("RETRY_BILLING"),
				List.of("RECONCILE_PAYMENT"),
				List.of("RECONCILE_REFUND"));
		org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(jdbc).queryForList(sql.capture());
		assertThat(sql.getValue()).contains("status='PROCESSING' AND reconciliation_attempts<10", "status='UNKNOWN' AND reconciliation_attempts<10");
	}

	private static Map<String,Object> row(String type, long referenceId, Timestamp createdAt, Integer attemptNo) {
		Map<String,Object> row = new LinkedHashMap<>();
		row.put("type", type);
		row.put("referenceId", referenceId);
		row.put("createdAt", createdAt);
		row.put("attemptNo", attemptNo);
		return row;
	}
}
