package com.pawcycle.backend.commerce;

import com.pawcycle.backend.commerce.billing.application.BillingApplicationService;
import com.pawcycle.backend.commerce.cart.application.CartApplicationService;
import com.pawcycle.backend.commerce.checkout.application.CheckoutApplicationService;
import com.pawcycle.backend.commerce.order.application.OrderApplicationService;
import com.pawcycle.backend.commerce.payment.application.PaymentApplicationService;
import com.pawcycle.backend.member.address.application.MemberAddressApplicationService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

/** Test-only compatibility helper for legacy integration fixtures. */
@Service
class CommerceFacade {
  private final CartApplicationService cartService;
  private final MemberAddressApplicationService addressService;
  private final BillingApplicationService billingService;
  private final CheckoutApplicationService checkoutService;
  private final PaymentApplicationService paymentService;
  private final OrderApplicationService orderService;

  CommerceFacade(
      CartApplicationService cartService,
      MemberAddressApplicationService addressService,
      BillingApplicationService billingService,
      CheckoutApplicationService checkoutService,
      PaymentApplicationService paymentService,
      OrderApplicationService orderService) {
    this.cartService = cartService;
    this.addressService = addressService;
    this.billingService = billingService;
    this.checkoutService = checkoutService;
    this.paymentService = paymentService;
    this.orderService = orderService;
  }

  CommercePayload cart(long memberId) {
    return cartService.get(memberId);
  }

  void addCartItem(long memberId, long skuId, int quantity) {
    cartService.add(memberId, skuId, quantity);
  }

  void updateCartItem(long memberId, long skuId, int quantity) {
    cartService.update(memberId, skuId, quantity);
  }

  void deleteCartItem(long memberId, long skuId) {
    cartService.delete(memberId, skuId);
  }

  List<CommerceRowResponse> addresses(long memberId) {
    return addressService.list(memberId);
  }

  long createAddress(long memberId, AddressRequest request) {
    return addressService.create(memberId, request);
  }

  void updateAddress(long memberId, long addressId, AddressRequest request) {
    addressService.update(memberId, addressId, request);
  }

  void deleteAddress(long memberId, long addressId) {
    addressService.delete(memberId, addressId);
  }

  void defaultAddress(long memberId, long addressId) {
    addressService.makeDefault(memberId, addressId);
  }

  void updateSubscriptionShipping(long memberId, long subscriptionId, AddressRequest request) {
    addressService.updateSubscriptionShipping(memberId, subscriptionId, request);
  }

  CommercePayload checkout(
      long memberId, String idempotencyKey, long addressId, Long memberCouponId) {
    return checkoutService.checkout(memberId, idempotencyKey, addressId, memberCouponId);
  }

  CommercePayload checkout(
      long memberId,
      String idempotencyKey,
      long addressId,
      Long memberCouponId,
      Long requestedCartVersion) {
    return checkoutService.checkout(
        memberId, idempotencyKey, addressId, memberCouponId, requestedCartVersion);
  }

  CommercePayload confirm(
      long memberId, String paymentKey, String providerOrderId, BigDecimal amount) {
    return paymentService.confirm(memberId, paymentKey, providerOrderId, amount);
  }

  List<CommerceRowResponse> orders(long memberId) {
    return orderService.orders(memberId);
  }

  CommercePayload order(long memberId, long orderId) {
    return orderService.order(memberId, orderId);
  }

  CommercePayload reorder(long memberId, long sourceOrderId, String idempotencyKey) {
    return orderService.reorder(memberId, sourceOrderId, idempotencyKey);
  }

  BillingPreparationResponse prepareBilling(long memberId) {
    return billingService.prepare(memberId);
  }

  void completeBilling(long memberId, String prepareToken, String authKey) {
    billingService.complete(memberId, prepareToken, authKey);
  }
}