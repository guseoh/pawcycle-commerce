package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

class ReturnServiceTests {
	@Test
	void createdAndIdempotentReturnUseTheSameProjection() {
		Map<String,Object> projection = new HashMap<>();
		projection.put("returnId", 4L); projection.put("status", "REQUESTED"); projection.put("reason", "damaged");
		projection.put("rejectionReason", null); projection.put("restock", null); projection.put("requestedAt", Timestamp.from(Instant.now()));
		assertThat(projection).containsKeys("returnId", "status", "reason", "rejectionReason", "restock", "requestedAt");

		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
		TransactionStatus status = mock(TransactionStatus.class);
		when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
		doNothing().when(manager).commit(status);
		when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(
				List.of(Map.of("id", 1L, "status", "PAID")), List.of(projection));
		ReturnService service = new ReturnService(jdbc, manager, mock(NotificationService.class), mock(AdminAuditService.class), mock(InventoryService.class), 7);

		assertThat(service.request(1L, 2L, "different")).containsExactlyInAnyOrderEntriesOf(projection);
	}
}
