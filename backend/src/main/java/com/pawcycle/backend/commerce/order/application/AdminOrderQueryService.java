package com.pawcycle.backend.commerce.order.application;

import com.pawcycle.backend.commerce.common.error.CommerceException;

import com.pawcycle.backend.commerce.order.model.OrderReadModel.AdminOrderView;
import com.pawcycle.backend.commerce.order.persistence.AdminOrderPersistenceAdapter;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AdminOrderQueryService {
  private final AdminOrderPersistenceAdapter orders;

  public AdminOrderQueryService(AdminOrderPersistenceAdapter orders) {
    this.orders = orders;
  }

  public List<AdminOrderView> list() {
    return orders.findAll();
  }

  public AdminOrderView get(long id) {
    AdminOrderView view = orders.find(id);
    if (view == null) throw new CommerceException(404, "ORDER_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    return view;
  }

}
