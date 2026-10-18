package com.pawcycle.backend.commerce.order.application;

import com.pawcycle.backend.commerce.common.error.CommerceException;
import com.pawcycle.backend.commerce.order.persistence.OrderPersistenceAdapter;
import com.pawcycle.backend.commerce.order.model.OrderView;
import com.pawcycle.backend.commerce.order.model.OrderReadModel;
import com.pawcycle.backend.commerce.order.persistence.QuickReorderPersistenceAdapter;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderApplicationService {
  private final OrderPersistenceAdapter orders;
  private final QuickReorderPersistenceAdapter quickReorders;
  private final TransactionTemplate transaction;
  private final Clock clock;
  private final int returnRequestDays;

  public OrderApplicationService(
      OrderPersistenceAdapter orders,
      QuickReorderPersistenceAdapter quickReorders,
      org.springframework.transaction.PlatformTransactionManager transactionManager,
      Clock clock,
      @Value("${pawcycle.commerce.return-request-days:7}") int returnRequestDays) {
    this.orders = orders;
    this.quickReorders = quickReorders;
    this.transaction = new TransactionTemplate(transactionManager);
    this.clock = clock;
    this.returnRequestDays = returnRequestDays;
  }

  public List<OrderReadModel.Summary> orders(long memberId) {
    return orders.findOrders(memberId);
  }

  public OrderReadModel.Detail order(long memberId, long orderId) {
    OrderView view = orders.findOrder(memberId, orderId);
    if (view == null) throw new CommerceException(404, "ORDER_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    return detail(view);
  }

  public OrderReadModel.Reorder reorder(long memberId, long sourceOrderId, String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
      throw new CommerceException(400, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key가 필요합니다.");
    }
    QuickReorderPersistenceAdapter.ReorderResult result =
        transaction.execute(
            status -> quickReorders.reorder(memberId, sourceOrderId, idempotencyKey));
    return new OrderReadModel.Reorder(
        result.addedItems().stream()
            .map(item -> new OrderReadModel.ReorderItem(item.skuId(), item.quantity()))
            .toList(),
        result.skippedItems().stream()
            .map(item -> new OrderReadModel.SkippedItem(item.skuId(), item.quantity(), item.reason()))
            .toList(),
        result.cartVersion());
  }

  private OrderReadModel.Detail detail(OrderView view) {
    List<String> actions = new ArrayList<>();
    OrderView.Delivery delivery = view.delivery();
    if ("PAID".equals(view.status())
        && delivery != null
        && "PREPARING".equals(delivery.status())
        && view.cancellation() == null) {
      actions.add("REQUEST_CANCELLATION");
    }
    Timestamp deliveredAt = delivery == null ? null : delivery.deliveredAt();
    boolean returnWindowOpen =
        deliveredAt != null
            && !deliveredAt.toInstant()
                .plus(returnRequestDays, ChronoUnit.DAYS)
                .isBefore(clock.instant());
    if (delivery != null
        && "DELIVERED".equals(delivery.status())
        && returnWindowOpen
        && view.returnRequest() == null) {
      actions.add("REQUEST_RETURN");
    }
    return new OrderReadModel.Detail(view, actions);
  }
}
