package com.pawcycle.backend.commerce.checkout.persistence;

import com.pawcycle.backend.commerce.CommerceOrderEntity;
import com.pawcycle.backend.commerce.CommerceOrderRepository;
import com.pawcycle.backend.commerce.MemberCouponRepository;
import com.pawcycle.backend.commerce.PaymentEntity;
import com.pawcycle.backend.commerce.PaymentRepository;
import com.pawcycle.backend.commerce.OrderItemRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CheckoutExpirationPersistenceAdapter {
  private final PaymentRepository payments;
  private final CommerceOrderRepository orders;
  private final OrderItemRepository orderItems;
  private final MemberCouponRepository memberCoupons;
  private final Clock clock;

  public CheckoutExpirationPersistenceAdapter(
      PaymentRepository payments,
      CommerceOrderRepository orders,
      OrderItemRepository orderItems,
      MemberCouponRepository memberCoupons,
      Clock clock) {
    this.payments = payments;
    this.orders = orders;
    this.orderItems = orderItems;
    this.memberCoupons = memberCoupons;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<Long> findDuePaymentIds(int batchSize) {
    return payments.findDuePaymentIds(now(), PageRequest.of(0, batchSize));
  }

  @Transactional
  public ExpirationTarget findForUpdate(long paymentId) {
    PaymentEntity payment = payments.findByIdForUpdate(paymentId).orElse(null);
    if (payment == null) return null;
    CommerceOrderEntity order = orders.findByIdForUpdate(payment.getOrderId()).orElse(null);
    if (order == null) return null;
    return new ExpirationTarget(payment.getId(), order.getId(), payment.getStatus(), order.getStatus());
  }

  @Transactional(readOnly = true)
  public List<CheckoutCartItem> findOrderItems(long orderId) {
    return orderItems.findAllByOrderId(orderId).stream()
        .map(item -> new CheckoutCartItem(item.getSkuId(), item.getQuantity()))
        .toList();
  }

  @Transactional
  public void releaseCoupon(long orderId) {
    memberCoupons.releaseReserved(orderId);
  }

  @Transactional
  public void markExpired(long paymentId, long orderId) {
    PaymentEntity payment = payments.findByIdForUpdate(paymentId).orElse(null);
    CommerceOrderEntity order = orders.findByIdForUpdate(orderId).orElse(null);
    if (payment != null) payment.markExpired(now());
    if (order != null) order.markExpired();
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault());
  }

  public record ExpirationTarget(long paymentId, long orderId, String paymentStatus, String orderStatus) {}

  public record CheckoutCartItem(long skuId, int quantity) {}
}
