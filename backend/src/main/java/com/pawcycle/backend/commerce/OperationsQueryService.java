package com.pawcycle.backend.commerce;

import com.pawcycle.backend.commerce.operations.api.OperationsPendingResponse;
import com.pawcycle.backend.commerce.operations.persistence.OperationsQueryRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/** Read projection only; it does not introduce an operations table. */
@Service
public class OperationsQueryService {
  private final OperationsQueryRepository queries;

  public OperationsQueryService(OperationsQueryRepository queries) {
    this.queries = queries;
  }

  public List<OperationsPendingResponse> pending() {
    return queries.findPending().stream()
        .map(
            row ->
                new OperationsPendingResponse(
                    row.type(),
                    row.referenceId(),
                    row.createdAt(),
                    row.attemptNo(),
                    switch (row.type()) {
            case "DELIVERY_PREPARING" -> List.of("SHIP_DELIVERY");
            case "DELIVERY_SHIPPED" -> List.of("COMPLETE_DELIVERY", "FAIL_DELIVERY");
            case "DELIVERY_FAILED" -> List.of("RESHIP_DELIVERY");
            case "RETURN_REQUESTED" -> List.of("APPROVE_RETURN", "REJECT_RETURN");
            case "RETURN_APPROVED" -> List.of("RECEIVE_RETURN");
            case "REFUND_READY" -> List.of("PROCESS_REFUND");
            case "REFUND_PROCESSING" -> List.of("RECONCILE_REFUND");
            case "PAYMENT_UNKNOWN" -> List.of("RECONCILE_PAYMENT");
            case "REFUND_UNKNOWN" -> List.of("RECONCILE_REFUND");
            case "REFUND_FAILED" ->
                row.attemptNo() != null && row.attemptNo() < 3
                    ? List.of("RETRY_REFUND")
                    : List.of();
            case "PAYMENT_PROCESSING" -> List.of("RECONCILE_PAYMENT");
            case "PAYMENT_RETRY_STOCK_UNAVAILABLE" -> List.of("RETRY_BILLING");
            default -> List.of();
                    }))
        .toList();
  }
}
