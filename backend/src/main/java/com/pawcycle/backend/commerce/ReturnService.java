package com.pawcycle.backend.commerce;

import com.pawcycle.backend.commerce.returning.api.ReturnResponse;
import com.pawcycle.backend.commerce.returning.persistence.ReturnPersistenceAdapter;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ReturnService {
  private final ReturnPersistenceAdapter returns;
  private final TransactionTemplate transaction;
  private final NotificationService notifications;
  private final AdminAuditService audits;
  private final InventoryService inventory;
  private final int requestDays;
  private final Clock clock;

  public ReturnService(
      ReturnPersistenceAdapter returns,
      org.springframework.transaction.PlatformTransactionManager transactionManager,
      NotificationService notifications,
      AdminAuditService audits,
      InventoryService inventory,
      @Value("${pawcycle.commerce.return-request-days:7}") int requestDays,
      Clock clock) {
    this.returns = returns;
    this.transaction = new TransactionTemplate(transactionManager);
    this.notifications = notifications;
    this.audits = audits;
    this.inventory = inventory;
    this.requestDays = requestDays;
    this.clock = clock;
  }

  public ReturnResponse request(long memberId, long orderId, String reason) {
    return transaction.execute(
        status -> {
          ReturnPersistenceAdapter.OrderLock order = returns.findOrderForUpdate(memberId, orderId);
          if (order == null) throw notFound("ORDER_NOT_FOUND");
          ReturnPersistenceAdapter.ReturnView existing = returns.findByOrderForUpdate(orderId);
          if (existing != null) return response(existing);
          if (returns.hasCancellationForUpdate(orderId)) {
            throw new CommerceException(409, "RETURN_NOT_ALLOWED", "취소가 진행된 주문은 반품할 수 없습니다.");
          }
          ReturnPersistenceAdapter.DeliveryView delivery = returns.findDeliveryForUpdate(orderId);
          if (!"PAID".equals(order.status())
              || delivery == null
              || !"DELIVERED".equals(delivery.status())
              || delivery.deliveredAt() == null
              || delivery.deliveredAt().toInstant().plus(requestDays, ChronoUnit.DAYS).isBefore(clock.instant())) {
            throw new CommerceException(409, "RETURN_NOT_ALLOWED", "반품 요청 가능 기간이 아닙니다.");
          }
          long returnId = returns.create(orderId, reason);
          return response(returns.find(returnId));
        });
  }

  public ReturnResponse approve(long adminId, long id) {
    return decide(adminId, id, "APPROVED", null);
  }

  public ReturnResponse reject(long adminId, long id, String reason) {
    return decide(adminId, id, "REJECTED", reason);
  }

  private ReturnResponse decide(long adminId, long id, String state, String reason) {
    return transaction.execute(
        status -> {
          ReturnPersistenceAdapter.ReturnDecision row = returns.findForDecision(id);
          if (row == null) throw notFound("RETURN_NOT_FOUND");
          if (!"REQUESTED".equals(row.status())) {
            throw new CommerceException(409, "RETURN_STATE_CONFLICT", "반품 상태를 전이할 수 없습니다.");
          }
          returns.decide(id, state, reason, adminId);
          notifications.create(
              row.memberId(),
              "APPROVED".equals(state) ? "RETURN_APPROVED" : "RETURN_REJECTED",
              "RETURN",
              id);
          audits.append(adminId, "APPROVED".equals(state) ? "RETURN_APPROVE" : "RETURN_REJECT", "RETURN", id);
          return response(returns.find(id));
        });
  }

  public ReturnResponse receive(long adminId, long id, boolean restock) {
    return transaction.execute(
        status -> {
          ReturnPersistenceAdapter.ReceiveTarget row = returns.findForReceive(id);
          if (row == null) throw notFound("RETURN_NOT_FOUND");
          if (!"APPROVED".equals(row.status())) {
            throw new CommerceException(409, "RETURN_STATE_CONFLICT", "승인된 반품만 수령할 수 있습니다.");
          }
          if (restock) {
            for (ReturnPersistenceAdapter.OrderItem item : returns.findOrderItems(row.orderId())) {
              inventory.restoreReturn(item.skuId(), item.quantity(), id);
            }
          }
          returns.receive(id, restock, adminId);
          returns.createRefund(row.orderId(), id);
          audits.append(adminId, "RETURN_RECEIVE", "RETURN", id);
          return response(returns.find(id));
        });
  }

  private static ReturnResponse response(ReturnPersistenceAdapter.ReturnView view) {
    return new ReturnResponse(
        view.returnId(),
        view.status(),
        view.reason(),
        view.rejectionReason(),
        view.restock(),
        view.requestedAt(),
        view.decidedAt(),
        view.receivedAt(),
        view.completedAt());
  }

  private static CommerceException notFound(String code) {
    return new CommerceException(404, code, "요청한 리소스를 찾을 수 없습니다.");
  }
}
