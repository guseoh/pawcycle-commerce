package com.pawcycle.backend.commerce;

import com.pawcycle.backend.commerce.billing.application.BillingApplicationService;
import com.pawcycle.backend.commerce.cart.application.CartApplicationService;
import com.pawcycle.backend.commerce.cart.api.CartResponse;
import com.pawcycle.backend.commerce.checkout.application.CheckoutApplicationService;
import com.pawcycle.backend.commerce.checkout.api.CheckoutResponse;
import com.pawcycle.backend.commerce.order.application.OrderApplicationService;
import com.pawcycle.backend.commerce.order.api.OrderResponse;
import com.pawcycle.backend.commerce.order.api.OrderSummaryResponse;
import com.pawcycle.backend.commerce.payment.application.PaymentApplicationService;
import com.pawcycle.backend.commerce.payment.api.PaymentResponse;
import com.pawcycle.backend.member.address.api.AddressResponse;
import com.pawcycle.backend.member.address.application.MemberAddressApplicationService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    return CommercePayload.from(cartMap(cartService.get(memberId)));
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
    return addressService.list(memberId).stream()
        .map(address -> new CommerceRowResponse(addressMap(address)))
        .toList();
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
    return CommercePayload.from(checkoutMap(checkoutService.checkout(memberId, idempotencyKey, addressId, memberCouponId)));
  }

  CommercePayload checkout(
      long memberId,
      String idempotencyKey,
      long addressId,
      Long memberCouponId,
      Long requestedCartVersion) {
    return CommercePayload.from(checkoutMap(checkoutService.checkout(
        memberId, idempotencyKey, addressId, memberCouponId, requestedCartVersion)));
  }

  CommercePayload confirm(
      long memberId, String paymentKey, String providerOrderId, BigDecimal amount) {
    return CommercePayload.from(paymentMap(paymentService.confirm(memberId, paymentKey, providerOrderId, amount)));
  }

  List<CommerceRowResponse> orders(long memberId) {
    return orderService.orders(memberId).stream()
        .map(summary -> new CommerceRowResponse(summaryMap(summary)))
        .toList();
  }

  CommercePayload order(long memberId, long orderId) {
    return CommercePayload.from(orderMap(orderService.order(memberId, orderId)));
  }

  CommercePayload reorder(long memberId, long sourceOrderId, String idempotencyKey) {
    var result = orderService.reorder(memberId, sourceOrderId, idempotencyKey);
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("addedItems", result.addedItems().stream().map(item -> Map.of("skuId", item.skuId(), "quantity", item.quantity())).toList());
    values.put("skippedItems", result.skippedItems().stream().map(item -> Map.of("skuId", item.skuId(), "quantity", item.quantity(), "reason", item.reason())).toList());
    values.put("cartVersion", result.cartVersion());
    return CommercePayload.from(values);
  }

  BillingPreparationResponse prepareBilling(long memberId) {
    return billingService.prepare(memberId);
  }

  void completeBilling(long memberId, String prepareToken, String authKey) {
    billingService.complete(memberId, prepareToken, authKey);
  }

  private static Map<String, Object> cartMap(CartResponse response) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("items", response.items().stream().map(item -> {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("skuId", item.skuId());
      map.put("quantity", item.quantity());
      map.put("skuCode", item.skuCode());
      map.put("skuName", item.skuName());
      map.put("price", item.price());
      map.put("unitPrice", item.unitPrice());
      map.put("lineAmount", item.lineAmount());
      map.put("productId", item.productId());
      map.put("productName", item.productName());
      map.put("availableQuantity", item.availableQuantity());
      map.put("purchasable", item.purchasable());
      return map;
    }).toList());
    values.put("version", response.version());
    values.put("pricing", Map.of(
        "originalAmount", response.pricing().originalAmount(),
        "subtotalAmount", response.pricing().subtotalAmount(),
        "discountAmount", response.pricing().discountAmount(),
        "shippingFee", response.pricing().shippingFee(),
        "finalAmount", response.pricing().finalAmount(),
        "paymentAmount", response.pricing().paymentAmount()));
    return values;
  }

  private static Map<String, Object> addressMap(AddressResponse address) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("addressId", address.addressId());
    values.put("name", address.name());
    values.put("recipientName", address.recipientName());
    values.put("recipientPhone", address.recipientPhone());
    values.put("postalCode", address.postalCode());
    values.put("addressLine1", address.addressLine1());
    values.put("addressLine2", address.addressLine2());
    values.put("isDefault", address.isDefault());
    return values;
  }

  private static Map<String, Object> checkoutMap(CheckoutResponse response) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("orderId", response.orderId());
    values.put("orderNumber", response.orderNumber());
    values.put("paymentId", response.paymentId());
    values.put("providerOrderId", response.providerOrderId());
    values.put("orderName", response.orderName());
    values.put("amount", response.amount());
    values.put("pricing", Map.of(
        "originalAmount", response.pricing().originalAmount(),
        "subtotalAmount", response.pricing().subtotalAmount(),
        "discountAmount", response.pricing().discountAmount(),
        "shippingFee", response.pricing().shippingFee(),
        "finalAmount", response.pricing().finalAmount(),
        "paymentAmount", response.pricing().paymentAmount()));
    return values;
  }

  private static Map<String, Object> paymentMap(PaymentResponse response) {
    return Map.of("paymentId", response.paymentId(), "orderId", response.orderId(), "status", response.status());
  }

  private static Map<String, Object> summaryMap(OrderSummaryResponse response) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("orderId", response.orderId());
    values.put("orderNumber", response.orderNumber());
    values.put("source", response.source());
    values.put("status", response.status());
    values.put("paymentAmount", response.paymentAmount());
    values.put("createdAt", response.createdAt());
    values.put("paidAt", response.paidAt());
    return values;
  }

  private static Map<String, Object> orderMap(OrderResponse response) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("orderId", response.orderId());
    values.put("orderNumber", response.orderNumber());
    values.put("source", response.source());
    values.put("status", response.status());
    values.put("originalAmount", response.originalAmount());
    values.put("discountAmount", response.discountAmount());
    values.put("shippingFee", response.shippingFee());
    values.put("paymentAmount", response.paymentAmount());
    values.put("recipientName", response.recipientName());
    values.put("recipientPhone", response.recipientPhone());
    values.put("postalCode", response.postalCode());
    values.put("addressLine1", response.addressLine1());
    values.put("addressLine2", response.addressLine2());
    values.put("createdAt", response.createdAt());
    values.put("paidAt", response.paidAt());
    values.put("items", response.items().stream().map(item -> {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("skuId", item.skuId());
      map.put("snapshotQuality", item.snapshotQuality());
      map.put("skuCodeSnapshot", item.skuCodeSnapshot());
      map.put("productNameSnapshot", item.productNameSnapshot());
      map.put("skuNameSnapshot", item.skuNameSnapshot());
      map.put("unitPrice", item.unitPrice());
      map.put("quantity", item.quantity());
      map.put("lineAmount", item.lineAmount());
      return map;
    }).toList());
    if (response.payment() == null) {
      values.put("payment", null);
    } else {
      Map<String, Object> payment = new LinkedHashMap<>();
      payment.put("paymentId", response.payment().paymentId());
      payment.put("type", response.payment().type());
      payment.put("provider", response.payment().provider());
      payment.put("status", response.payment().status());
      payment.put("amount", response.payment().amount());
      payment.put("attemptNo", response.payment().attemptNo());
      payment.put("providerStatus", response.payment().providerStatus());
      values.put("payment", payment);
    }
    if (response.delivery() == null) {
      values.put("delivery", null);
    } else {
      Map<String, Object> delivery = new LinkedHashMap<>();
      delivery.put("deliveryId", response.delivery().deliveryId());
      delivery.put("orderId", response.delivery().orderId());
      delivery.put("status", response.delivery().status());
      delivery.put("carrierCode", response.delivery().carrierCode());
      delivery.put("trackingNumber", response.delivery().trackingNumber());
      delivery.put("failureReason", response.delivery().failureReason());
      delivery.put("shippedAt", response.delivery().shippedAt());
      delivery.put("deliveredAt", response.delivery().deliveredAt());
      delivery.put("failedAt", response.delivery().failedAt());
      delivery.put("cancelledAt", response.delivery().cancelledAt());
      values.put("delivery", delivery);
    }
    values.put("cancellation", response.cancellation() == null ? null : response.cancellation());
    values.put("return", response.returnRequest() == null ? null : response.returnRequest());
    values.put("refunds", response.refunds());
    values.put("availableActions", response.availableActions());
    return values;
  }
}
