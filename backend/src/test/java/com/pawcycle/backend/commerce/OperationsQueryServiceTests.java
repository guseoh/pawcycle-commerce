package com.pawcycle.backend.commerce;

import com.pawcycle.backend.commerce.operations.application.OperationsQueryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import com.pawcycle.backend.commerce.operations.api.OperationsPendingResponse;
import com.pawcycle.backend.commerce.operations.persistence.OperationsQueryRepository;
import com.pawcycle.backend.commerce.operations.persistence.OperationsQueryRepository.PendingRow;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class OperationsQueryServiceTests {
  @Test
  void exposesApprovedOperationsWithOnlyExecutableActions() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    Timestamp now = Timestamp.from(Instant.now());
    List<PendingRow> rows =
        List.of(
            row("DELIVERY_PREPARING", 1L, now, null),
            row("DELIVERY_SHIPPED", 2L, now, null),
            row("DELIVERY_FAILED", 3L, now, null),
            row("RETURN_APPROVED", 4L, now, null),
            row("REFUND_PROCESSING", 5L, now, 1),
            row("PAYMENT_PROCESSING", 6L, now, null),
            row("PAYMENT_RETRY_STOCK_UNAVAILABLE", 7L, now, 1),
            row("PAYMENT_UNKNOWN", 8L, now, null),
            row("REFUND_UNKNOWN", 9L, now, 1));
    when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<PendingRow>>any())).thenReturn(rows);

    OperationsQueryRepository queries = new OperationsQueryRepository(jdbc);
    List<OperationsPendingResponse> result = new OperationsQueryService(queries).pending();

    assertThat(result)
        .extracting(OperationsPendingResponse::availableActions)
        .containsExactly(
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
    verify(jdbc).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<PendingRow>>any());
    assertThat(sql.getValue())
        .contains(
            "status='PROCESSING' AND reconciliation_attempts<10",
            "status='UNKNOWN' AND reconciliation_attempts<10");
  }

  private static PendingRow row(
      String type, long referenceId, Timestamp createdAt, Integer attemptNo) {
    return new PendingRow(type, referenceId, createdAt, attemptNo);
  }
}
