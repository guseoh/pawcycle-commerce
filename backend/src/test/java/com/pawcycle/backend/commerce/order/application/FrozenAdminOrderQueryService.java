package com.pawcycle.backend.commerce.order.application;

import com.pawcycle.backend.commerce.common.error.CommerceException;

import com.pawcycle.backend.commerce.order.api.AdminOrderResponse;
import com.pawcycle.backend.commerce.order.persistence.AdminOrderPersistenceAdapter;
import java.util.List;
import org.springframework.stereotype.Service;

public class FrozenAdminOrderQueryService {
  private final AdminOrderPersistenceAdapter orders;

  public FrozenAdminOrderQueryService(AdminOrderPersistenceAdapter orders) {
    this.orders = orders;
  }

  public List<AdminOrderResponse> list() {
    return orders.findAll().stream().map(FrozenAdminOrderQueryService::response).toList();
  }

  public AdminOrderResponse get(long id) {
    com.pawcycle.backend.commerce.order.model.OrderReadModel.AdminOrderView view = orders.find(id);
    if (view == null) throw new CommerceException(404, "ORDER_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    return response(view);
  }

  private static AdminOrderResponse response(com.pawcycle.backend.commerce.order.model.OrderReadModel.AdminOrderView view) {
    return new AdminOrderResponse(
        view.orderId(),
        view.orderNumber(),
        view.memberId(),
        view.status(),
        view.paymentAmount(),
        view.createdAt());
  }
}
