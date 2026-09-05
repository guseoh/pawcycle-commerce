package com.pawcycle.backend.commerce.checkout.persistence;

import com.pawcycle.backend.catalog.product.domain.ProductStatus;
import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.commerce.CartItemRepository;
import com.pawcycle.backend.commerce.CheckoutIdempotencyEntity;
import com.pawcycle.backend.commerce.CheckoutIdempotencyId;
import com.pawcycle.backend.commerce.CheckoutIdempotencyRepository;
import com.pawcycle.backend.commerce.CommerceOrderEntity;
import com.pawcycle.backend.commerce.CommerceOrderItemEntity;
import com.pawcycle.backend.commerce.CommerceOrderRepository;
import com.pawcycle.backend.commerce.CouponEntity;
import com.pawcycle.backend.commerce.CouponRepository;
import com.pawcycle.backend.commerce.MemberCouponEntity;
import com.pawcycle.backend.commerce.MemberCouponRepository;
import com.pawcycle.backend.commerce.PaymentEntity;
import com.pawcycle.backend.commerce.PaymentRepository;
import com.pawcycle.backend.member.domain.MemberAddress;
import com.pawcycle.backend.member.persistence.MemberAddressRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class CheckoutPersistenceAdapter {
  private final EntityManager entityManager;
  private final CheckoutIdempotencyRepository idempotencies;
  private final MemberAddressRepository addresses;
  private final CartItemRepository cartItems;
  private final CommerceOrderRepository orders;
  private final PaymentRepository payments;
  private final MemberCouponRepository memberCoupons;
  private final CouponRepository coupons;
  private final Clock clock;

  public CheckoutPersistenceAdapter(
      EntityManager entityManager,
      CheckoutIdempotencyRepository idempotencies,
      MemberAddressRepository addresses,
      CartItemRepository cartItems,
      CommerceOrderRepository orders,
      PaymentRepository payments,
      MemberCouponRepository memberCoupons,
      CouponRepository coupons,
      Clock clock) {
    this.entityManager = entityManager;
    this.idempotencies = idempotencies;
    this.addresses = addresses;
    this.cartItems = cartItems;
    this.orders = orders;
    this.payments = payments;
    this.memberCoupons = memberCoupons;
    this.coupons = coupons;
    this.clock = clock;
  }

  public CheckoutReplay findReplay(long memberId, String idempotencyKey) {
    CheckoutIdempotencyEntity result =
        idempotencies.findForUpdate(memberId, idempotencyKey).orElse(null);
    if (result == null) return null;
    CommerceOrderEntity order = orders.findById(result.getOrderId()).orElseThrow();
    PaymentEntity payment = payments.findById(result.getPaymentId()).orElseThrow();
    return new CheckoutReplay(
        result.getRequestFingerprint(),
        result.getRequestCartVersion(),
        order.getId(),
        order.getOrderNumber(),
        payment.getId(),
        payment.getProviderOrderId(),
        order.getPaymentAmount());
  }

  public CheckoutAddress findAddress(long memberId, long addressId) {
    return addresses
        .findByIdAndMemberId(addressId, memberId)
        .map(CheckoutPersistenceAdapter::address)
        .orElse(null);
  }

  public List<CheckoutCartItem> findCartItems(long cartId) {
    // Lock the cart rows first; the projection read intentionally follows separately so the
    // lock scope does not depend on provider-specific DTO-query locking behavior.
    cartItems.findAllByCartIdForUpdate(cartId);
    TypedQuery<CheckoutCartItemRow> query =
        entityManager.createQuery(
            """
            select new com.pawcycle.backend.commerce.checkout.persistence.CheckoutCartItemRow(
                item.id.skuId, item.quantity, sku.skuCode, sku.name, sku.price, product.name)
            from CartItemEntity item
            join Sku sku on sku.id = item.id.skuId
            join sku.product product
            where item.id.cartId = :cartId
            """,
            CheckoutCartItemRow.class);
    return query.setParameter("cartId", cartId).getResultList().stream()
        .map(CheckoutCartItemRow::toView)
        .toList();
  }

  public boolean isPurchasable(long skuId) {
    Long count =
        entityManager
            .createQuery(
                """
                select count(sku)
                from Sku sku
                join sku.product product
                join product.category category
                where sku.id = :skuId
                  and sku.status = :skuStatus
                  and product.status = :productStatus
                  and category.active = true
                """,
                Long.class)
            .setParameter("skuId", skuId)
            .setParameter("skuStatus", SkuStatus.ACTIVE)
            .setParameter("productStatus", ProductStatus.PUBLIC)
            .getSingleResult();
    return count == 1L;
  }

  public CouponRule findCouponRule(long memberId, long memberCouponId) {
    MemberCouponEntity memberCoupon =
        memberCoupons.findByIdForUpdate(memberCouponId).orElse(null);
    if (memberCoupon == null
        || memberCoupon.getMemberId() != memberId
        || !"AVAILABLE".equals(memberCoupon.getStatus())) return null;
    CouponEntity coupon = coupons.findByIdForUpdate(memberCoupon.getCouponId()).orElse(null);
    if (coupon == null || !coupon.isActive() || !isValidNow(coupon)) return null;
    return new CouponRule(
        coupon.getMinimumOrderAmount(),
        coupon.getDiscountValue(),
        coupon.getMaximumDiscountAmount(),
        coupon.getDiscountType());
  }

  public long createOrder(
      long memberId,
      String orderNumber,
      BigDecimal original,
      BigDecimal discount,
      BigDecimal amount,
      CheckoutAddress address) {
    CommerceOrderEntity order =
        orders.saveAndFlush(
            new CommerceOrderEntity(
                orderNumber,
                memberId,
                original,
                discount,
                BigDecimal.ZERO,
                amount,
                address.recipientName(),
                address.recipientPhone(),
                address.postalCode(),
                address.addressLine1(),
                address.addressLine2(),
                now()));
    return order.getId();
  }

  public long createPayment(long orderId, BigDecimal amount) {
    PaymentEntity payment =
        payments.saveAndFlush(
            new PaymentEntity(
                orderId,
                amount,
                "TOSS-" + UUID.randomUUID(),
                "pay-" + UUID.randomUUID(),
                now(),
                now().plus(30, ChronoUnit.MINUTES)));
    return payment.getId();
  }

  public String providerOrderId(long paymentId) {
    return payments.findById(paymentId).map(PaymentEntity::getProviderOrderId).orElseThrow();
  }

  public void createOrderItem(long orderId, CheckoutCartItem item) {
    entityManager.persist(
        new CommerceOrderItemEntity(
            orderId,
            item.skuId(),
            item.skuCode(),
            item.productName(),
            item.skuName(),
            item.price(),
            item.quantity()));
  }

  public void reserveCoupon(long orderId, long memberCouponId, long memberId) {
    memberCoupons.reserveIfAvailable(orderId, memberCouponId, memberId);
  }

  public void saveIdempotency(
      long memberId,
      String idempotencyKey,
      long orderId,
      long paymentId,
      String fingerprint) {
    idempotencies.save(
        new CheckoutIdempotencyEntity(
            new CheckoutIdempotencyId(memberId, idempotencyKey),
            orderId,
            paymentId,
            fingerprint,
            now()));
  }

  public void saveCartVersion(long memberId, String idempotencyKey, long cartVersion) {
    int updated = idempotencies.saveCartVersionIfAbsent(memberId, idempotencyKey, cartVersion);
    if (updated != 1) throw new IllegalStateException("Checkout 요청 버전을 저장할 수 없습니다.");
  }

  public CheckoutOrderPricing findOrderPricing(long orderId) {
    CommerceOrderEntity order = orders.findById(orderId).orElseThrow();
    return new CheckoutOrderPricing(
        order.getOriginalAmount(),
        order.getDiscountAmount(),
        order.getShippingFee(),
        order.getPaymentAmount());
  }

  public List<String> findProductNames(long orderId) {
    return entityManager
        .createQuery(
            "select item.productNameSnapshot from CommerceOrderItemEntity item"
                + " where item.orderId = :orderId order by item.id",
            String.class)
        .setParameter("orderId", orderId)
        .getResultList();
  }

  private boolean isValidNow(CouponEntity coupon) {
    LocalDateTime now = now();
    return !coupon.getValidFrom().isAfter(now) && coupon.getValidUntil().isAfter(now);
  }

  private static CheckoutAddress address(MemberAddress address) {
    return new CheckoutAddress(
        address.getRecipientName(),
        address.getRecipientPhone(),
        address.getPostalCode(),
        address.getAddressLine1(),
        address.getAddressLine2());
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock);
  }
}
