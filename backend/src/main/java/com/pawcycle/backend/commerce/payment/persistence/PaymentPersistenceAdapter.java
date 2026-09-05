package com.pawcycle.backend.commerce.payment.persistence;

import com.pawcycle.backend.commerce.CartEntity;
import com.pawcycle.backend.commerce.CartItemEntity;
import com.pawcycle.backend.commerce.CartItemId;
import com.pawcycle.backend.commerce.CartItemRepository;
import com.pawcycle.backend.commerce.CartRepository;
import com.pawcycle.backend.commerce.CommerceOrderEntity;
import com.pawcycle.backend.commerce.CommerceOrderItemEntity;
import com.pawcycle.backend.commerce.CommerceOrderRepository;
import com.pawcycle.backend.commerce.MemberCouponRepository;
import com.pawcycle.backend.commerce.OrderItemRepository;
import com.pawcycle.backend.commerce.PaymentEntity;
import com.pawcycle.backend.commerce.PaymentRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentPersistenceAdapter {
  private final PaymentRepository payments;
  private final CommerceOrderRepository orders;
  private final OrderItemRepository orderItems;
  private final MemberCouponRepository memberCoupons;
  private final CartRepository carts;
  private final CartItemRepository cartItems;
  private final Clock clock;

  public PaymentPersistenceAdapter(
      PaymentRepository payments,
      CommerceOrderRepository orders,
      OrderItemRepository orderItems,
      MemberCouponRepository memberCoupons,
      CartRepository carts,
      CartItemRepository cartItems,
      Clock clock) {
    this.payments = payments;
    this.orders = orders;
    this.orderItems = orderItems;
    this.memberCoupons = memberCoupons;
    this.carts = carts;
    this.cartItems = cartItems;
    this.clock = clock;
  }

  public PaymentWork findByProviderOrderIdForUpdate(String providerOrderId) {
    return payments
        .findByProviderOrderIdForUpdate(providerOrderId)
        .map(this::paymentWork)
        .orElse(null);
  }

  public void markProcessing(long paymentId, String paymentKey) {
    payments.findById(paymentId).ifPresent(payment -> payment.markProcessing(paymentKey));
  }

  public PaymentState findForUpdate(long paymentId) {
    return payments
        .findByIdForUpdate(paymentId)
        .map(payment -> new PaymentState(payment.getId(), payment.getOrderId(), payment.getStatus()))
        .orElse(null);
  }

  public List<OrderItem> findOrderItems(long orderId) {
    return orderItems
        .findAllByOrderId(orderId)
        .stream()
        .map(PaymentPersistenceAdapter::orderItem)
        .toList();
  }

  public void markSucceeded(long paymentId, String paymentKey) {
    payments
        .findById(paymentId)
        .ifPresent(payment -> payment.markSucceeded(paymentKey, now()));
  }

  public void markFailed(long paymentId) {
    payments.findById(paymentId).ifPresent(payment -> payment.markFailed(now()));
  }

  public void markUnknown(long paymentId) {
    payments.findById(paymentId).ifPresent(PaymentEntity::markUnknown);
  }

  public void markOrderPaid(long orderId) {
    orders.findById(orderId).ifPresent(order -> order.markPaid(now()));
  }

  public void markOrderPaymentFailed(long orderId) {
    orders.findById(orderId).ifPresent(CommerceOrderEntity::markPaymentFailed);
  }

  public long findMemberId(long orderId) {
    return orders.findById(orderId).map(CommerceOrderEntity::getMemberId).orElseThrow();
  }

  public void useReservedCoupon(long orderId) {
    memberCoupons.useReserved(orderId, now());
  }

  public void releaseReservedCoupon(long orderId) {
    memberCoupons.releaseReserved(orderId);
  }

  public void consumeCart(long memberId, long orderId) {
    CartEntity cart = lockCart(memberId);
    boolean changed = false;
    for (OrderItem orderItem : findOrderItems(orderId)) {
      CartItemEntity cartItem =
          cartItems
              .findByIdForUpdate(new CartItemId(cart.getId(), orderItem.skuId()))
              .orElse(null);
      if (cartItem == null) continue;
      if (cartItem.getQuantity() <= orderItem.quantity()) {
        cartItems.delete(cartItem);
      } else {
        cartItem.updateQuantity(cartItem.getQuantity() - orderItem.quantity());
      }
      changed = true;
    }
    if (changed) cart.incrementVersion(now());
  }

  private CartEntity lockCart(long memberId) {
    return carts
        .findByMemberIdForUpdate(memberId)
        .orElseGet(() -> carts.saveAndFlush(new CartEntity(memberId, now())));
  }

  private PaymentWork paymentWork(PaymentEntity payment) {
    CommerceOrderEntity order = payment.getOrder();
    return new PaymentWork(
        payment.getId(),
        payment.getOrderId(),
        payment.getAmount(),
        payment.getStatus(),
        payment.getPaymentKey(),
        order.getMemberId(),
        order.getStatus());
  }

  private static OrderItem orderItem(CommerceOrderItemEntity item) {
    return new OrderItem(item.getSkuId(), item.getQuantity());
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock);
  }

  public record PaymentWork(
      long paymentId,
      long orderId,
      BigDecimal amount,
      String status,
      String paymentKey,
      long memberId,
      String orderStatus) {}

  public record PaymentState(long paymentId, long orderId, String status) {}

  public record OrderItem(long skuId, int quantity) {}
}
