package com.pawcycle.backend.commerce.order.persistence;

import com.pawcycle.backend.commerce.CommerceOrderEntity;
import com.pawcycle.backend.commerce.CommerceOrderRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AdminOrderPersistenceAdapter {
  private final CommerceOrderRepository orders;

  public AdminOrderPersistenceAdapter(CommerceOrderRepository orders) {
    this.orders = orders;
  }

  @Transactional(readOnly = true)
  public List<AdminOrderView> findAll() {
    return orders.findAllByOrderByIdDesc().stream().map(this::view).toList();
  }

  @Transactional(readOnly = true)
  public AdminOrderView find(long orderId) {
    return orders.findById(orderId).map(this::view).orElse(null);
  }

  private AdminOrderView view(CommerceOrderEntity order) {
    return new AdminOrderView(
        order.getId(),
        order.getOrderNumber(),
        order.getMemberId(),
        order.getStatus(),
        order.getPaymentAmount(),
        Timestamp.valueOf(order.getCreatedAt()));
  }

  public record AdminOrderView(
      long orderId,
      String orderNumber,
      long memberId,
      String status,
      BigDecimal paymentAmount,
      Timestamp createdAt) {}
}
