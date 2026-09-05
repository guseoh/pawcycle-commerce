package com.pawcycle.backend.commerce;

import com.pawcycle.backend.commerce.order.api.AdminOrderResponse;
import com.pawcycle.backend.commerce.order.persistence.AdminOrderPersistenceAdapter;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AdminOrderQueryService {
  private final AdminOrderPersistenceAdapter orders;

  public AdminOrderQueryService(AdminOrderPersistenceAdapter orders) {
    this.orders = orders;
  }

  public List<AdminOrderResponse> list() {
    return orders.findAll().stream().map(AdminOrderQueryService::response).toList();
  }

  public AdminOrderResponse get(long id) {
    AdminOrderPersistenceAdapter.AdminOrderView view = orders.find(id);
    if (view == null) throw new CommerceException(404, "ORDER_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    return response(view);
  }

  private static AdminOrderResponse response(AdminOrderPersistenceAdapter.AdminOrderView view) {
    return new AdminOrderResponse(
        view.orderId(),
        view.orderNumber(),
        view.memberId(),
        view.status(),
        view.paymentAmount(),
        view.createdAt());
  }
}
