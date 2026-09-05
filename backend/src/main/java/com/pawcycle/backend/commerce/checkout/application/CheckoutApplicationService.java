package com.pawcycle.backend.commerce.checkout.application;

import com.pawcycle.backend.commerce.CommerceException;
import com.pawcycle.backend.commerce.InventoryService;
import com.pawcycle.backend.commerce.cart.persistence.CartPersistenceAdapter;
import com.pawcycle.backend.commerce.checkout.api.CheckoutPricingResponse;
import com.pawcycle.backend.commerce.checkout.api.CheckoutResponse;
import com.pawcycle.backend.commerce.checkout.persistence.CheckoutAddress;
import com.pawcycle.backend.commerce.checkout.persistence.CheckoutCartItem;
import com.pawcycle.backend.commerce.checkout.persistence.CheckoutOrderPricing;
import com.pawcycle.backend.commerce.checkout.persistence.CheckoutPersistenceAdapter;
import com.pawcycle.backend.commerce.checkout.persistence.CheckoutReplay;
import com.pawcycle.backend.commerce.checkout.persistence.CouponRule;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CheckoutApplicationService {
  private final CheckoutPersistenceAdapter checkout;
  private final CartPersistenceAdapter cart;
  private final TransactionTemplate transaction;
  private final InventoryService inventory;

  public CheckoutApplicationService(
      CheckoutPersistenceAdapter checkout,
      CartPersistenceAdapter cart,
      org.springframework.transaction.PlatformTransactionManager transactionManager,
      InventoryService inventory) {
    this.checkout = checkout;
    this.cart = cart;
    this.transaction = new TransactionTemplate(transactionManager);
    this.inventory = inventory;
  }

  public CheckoutResponse checkout(
      long memberId, String idempotencyKey, long addressId, Long memberCouponId) {
    return checkout(memberId, idempotencyKey, addressId, memberCouponId, null);
  }

  public CheckoutResponse checkout(
      long memberId,
      String idempotencyKey,
      long addressId,
      Long memberCouponId,
      Long requestedCartVersion) {
    validateIdempotencyKey(idempotencyKey);
    return transaction.execute(
        status -> {
          CartPersistenceAdapter.CartLock cartLock = cart.lockForAdd(memberId);
          CheckoutReplay replay = checkout.findReplay(memberId, idempotencyKey);
          if (replay != null) {
            if (replay.requestCartVersion() == null
                || (requestedCartVersion != null
                    && requestedCartVersion.longValue() != replay.requestCartVersion())
                || !checkoutFingerprint(addressId, memberCouponId, replay.requestCartVersion())
                    .equals(replay.requestFingerprint())) {
              throw new CommerceException(
                  409, "IDEMPOTENCY_KEY_CONFLICT", "Idempotency-Key가 다른 요청에 사용되었습니다.");
            }
            return response(replay);
          }

          if (requestedCartVersion != null && requestedCartVersion != cartLock.version()) {
            throw new CommerceException(409, "CART_CHANGED", "장바구니가 변경되었습니다.");
          }
          String fingerprint = checkoutFingerprint(addressId, memberCouponId, cartLock.version());

          CheckoutAddress address = checkout.findAddress(memberId, addressId);
          if (address == null) notFound("ADDRESS_NOT_FOUND");
          List<CheckoutCartItem> items = checkout.findCartItems(cartLock.id());
          if (items.isEmpty()) {
            throw new CommerceException(409, "CART_EMPTY", "장바구니가 비어 있습니다.");
          }
          BigDecimal original = BigDecimal.ZERO;
          for (CheckoutCartItem item : items) {
            if (!checkout.isPurchasable(item.skuId())) {
              throw new CommerceException(409, "SKU_NOT_PURCHASABLE", "구매할 수 없는 SKU입니다.");
            }
            original =
                original.add(item.price().multiply(BigDecimal.valueOf(item.quantity())));
          }
          BigDecimal discount =
              memberCouponId == null
                  ? BigDecimal.ZERO
                  : calculateDiscount(memberId, memberCouponId, original);
          BigDecimal amount = original.subtract(discount);
          if (amount.compareTo(BigDecimal.valueOf(100)) < 0) {
            throw new CommerceException(409, "PAYMENT_AMOUNT_TOO_LOW", "결제 금액은 100원 이상이어야 합니다.");
          }

          String orderNumber = "O-" + UUID.randomUUID();
          long orderId =
              checkout.createOrder(memberId, orderNumber, original, discount, amount, address);
          long paymentId = checkout.createPayment(orderId, amount);
          for (CheckoutCartItem item : items) {
            inventory.reserve(item.skuId(), item.quantity(), paymentId);
            checkout.createOrderItem(orderId, item);
          }
          if (memberCouponId != null) checkout.reserveCoupon(orderId, memberCouponId, memberId);
          checkout.saveIdempotency(memberId, idempotencyKey, orderId, paymentId, fingerprint);
          checkout.saveCartVersion(memberId, idempotencyKey, cartLock.version());
          return new CheckoutResponse(
              orderId,
              orderNumber,
              paymentId,
              checkout.providerOrderId(paymentId),
              orderName(items),
              amount,
              pricing(original, discount, BigDecimal.ZERO, amount));
        });
  }

  private CheckoutResponse response(CheckoutReplay replay) {
    CheckoutOrderPricing order = checkout.findOrderPricing(replay.orderId());
    return new CheckoutResponse(
        replay.orderId(),
        replay.orderNumber(),
        replay.paymentId(),
        replay.providerOrderId(),
              orderNameFromNames(checkout.findProductNames(replay.orderId())),
        replay.amount(),
        pricing(
            order.originalAmount(),
            order.discountAmount(),
            order.shippingFee(),
            order.paymentAmount()));
  }

  private BigDecimal calculateDiscount(long memberId, long memberCouponId, BigDecimal original) {
    CouponRule coupon = checkout.findCouponRule(memberId, memberCouponId);
    if (coupon == null) {
      throw new CommerceException(409, "COUPON_UNAVAILABLE", "사용할 수 없는 쿠폰입니다.");
    }
    if (original.compareTo(coupon.minimumOrderAmount()) < 0) {
      throw new CommerceException(409, "COUPON_MINIMUM_ORDER", "최소 주문 금액을 충족하지 않습니다.");
    }
    BigDecimal discount =
        "PERCENTAGE".equals(coupon.discountType())
            ? original
                .multiply(coupon.discountValue())
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
            : coupon.discountValue();
    if (coupon.maximumDiscountAmount() != null) {
      discount = discount.min(coupon.maximumDiscountAmount());
    }
    return discount.min(original);
  }

  private static String orderName(List<CheckoutCartItem> items) {
    return items.getFirst().productName()
        + (items.size() > 1 ? " 외 " + (items.size() - 1) + "건" : "");
  }

  private static String orderNameFromNames(List<String> names) {
    return names.getFirst() + (names.size() > 1 ? " 외 " + (names.size() - 1) + "건" : "");
  }

  private static CheckoutPricingResponse pricing(
      BigDecimal original, BigDecimal discount, BigDecimal shipping, BigDecimal payment) {
    return new CheckoutPricingResponse(
        original, original.subtract(discount), discount, shipping, payment, payment);
  }

  private static String checkoutFingerprint(long addressId, Long memberCouponId, long cartVersion) {
    String payload = addressId + "|" + (memberCouponId == null ? "none" : memberCouponId) + "|" + cartVersion;
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder(64);
      for (byte value : digest) result.append(String.format("%02x", value));
      return result.toString();
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
    }
  }

  private static void validateIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
      throw new CommerceException(400, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key가 필요합니다.");
    }
  }

  private static void notFound(String code) {
    throw new CommerceException(404, code, "요청한 리소스를 찾을 수 없습니다.");
  }
}
