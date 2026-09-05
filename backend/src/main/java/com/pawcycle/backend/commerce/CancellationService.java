package com.pawcycle.backend.commerce;

import com.pawcycle.backend.commerce.cancellation.api.CancellationResponse;
import com.pawcycle.backend.commerce.cancellation.persistence.CancellationPersistenceAdapter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CancellationService {
  private final CancellationPersistenceAdapter cancellations;
  private final TransactionTemplate transaction;
  private final InventoryService inventory;

  public CancellationService(
      CancellationPersistenceAdapter cancellations,
      org.springframework.transaction.PlatformTransactionManager transactionManager,
      InventoryService inventory) {
    this.cancellations = cancellations;
    this.transaction = new TransactionTemplate(transactionManager);
    this.inventory = inventory;
  }

  public CancellationResponse request(long memberId, long orderId, String reason) {
    return transaction.execute(
        status -> {
          CancellationPersistenceAdapter.OrderLock order =
              cancellations.findOrderForUpdate(memberId, orderId);
          if (order == null) {
            throw new CommerceException(404, "ORDER_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
          }
          CancellationPersistenceAdapter.CancellationView existing =
              cancellations.findExisting(orderId);
          if (existing != null) return response(existing);
          CancellationPersistenceAdapter.DeliveryLock delivery =
              cancellations.findDeliveryForUpdate(orderId);
          if (!"PAID".equals(order.status())
              || delivery == null
              || !"PREPARING".equals(delivery.status())
              || !cancellations.hasSuccessfulPayment(orderId)) {
            throw new CommerceException(409, "CANCELLATION_NOT_ALLOWED", "현재 주문은 취소할 수 없습니다.");
          }
          if (cancellations.hasReturnForUpdate(orderId)) {
            throw new CommerceException(409, "CANCELLATION_NOT_ALLOWED", "반품이 진행 중인 주문은 취소할 수 없습니다.");
          }
          long cancellationId = cancellations.create(orderId, reason);
          cancellations.cancelDelivery(delivery.deliveryId());
          for (CancellationPersistenceAdapter.OrderItem item : cancellations.findOrderItems(orderId)) {
            inventory.restoreCancellation(item.skuId(), item.quantity(), cancellationId);
          }
          cancellations.createRefund(orderId, cancellationId);
          return response(cancellations.find(cancellationId));
        });
  }

  private static CancellationResponse response(CancellationPersistenceAdapter.CancellationView view) {
    return new CancellationResponse(
        view.cancellationId(), view.status(), view.reason(), view.requestedAt(), view.completedAt());
  }
}
