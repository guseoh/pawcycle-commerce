package com.pawcycle.backend.commerce;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/*
 * Runtime mappings for the immutable V13–V19 Commerce schema.  Associations are deliberately
 * lazy and read-only; state transitions retain explicit SQL CAS/locking in application services.
 */
@Embeddable
class CartItemId implements Serializable {
  @Column(name = "cart_id")
  Long cartId;

  @Column(name = "sku_id")
  Long skuId;

  @Override
  public boolean equals(Object other) {
    return other instanceof CartItemId id
        && Objects.equals(cartId, id.cartId)
        && Objects.equals(skuId, id.skuId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(cartId, skuId);
  }
}

@Embeddable
class WishlistItemId implements Serializable {
  @Column(name = "member_id")
  Long memberId;

  @Column(name = "product_id")
  Long productId;

  @Override
  public boolean equals(Object other) {
    return other instanceof WishlistItemId id
        && Objects.equals(memberId, id.memberId)
        && Objects.equals(productId, id.productId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(memberId, productId);
  }
}

@Embeddable
class CheckoutIdempotencyId implements Serializable {
  @Column(name = "member_id")
  Long memberId;

  @Column(name = "idempotency_key", length = 128)
  String idempotencyKey;

  @Override
  public boolean equals(Object other) {
    return other instanceof CheckoutIdempotencyId id
        && Objects.equals(memberId, id.memberId)
        && Objects.equals(idempotencyKey, id.idempotencyKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(memberId, idempotencyKey);
  }
}

@Entity
@Table(name = "carts")
class CartEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "member_id", nullable = false)
  Long memberId;

  @Column(nullable = false)
  long version;

  @Column(name = "created_at", nullable = false)
  LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  LocalDateTime updatedAt;
}

@Entity
@Table(name = "cart_items")
class CartItemEntity {
  @EmbeddedId CartItemId id;

  @Column(nullable = false)
  int quantity;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "cart_id", insertable = false, updatable = false)
  CartEntity cart;
}

@Entity
@Table(name = "wishlist_items")
class WishlistItemEntity {
  @EmbeddedId WishlistItemId id;

  @Column(name = "created_at", nullable = false)
  LocalDateTime createdAt;
}

@Entity
@Table(name = "inventories")
class InventoryEntity {
  @Id
  @Column(name = "sku_id")
  Long skuId;

  @Column(name = "available_quantity", nullable = false)
  int availableQuantity;

  @Column(name = "reserved_quantity", nullable = false)
  int reservedQuantity;

  @Column(nullable = false)
  long version;
}

@Entity
@Table(name = "inventory_movements")
class InventoryMovementEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "sku_id", nullable = false)
  Long skuId;

  @Column(name = "payment_id")
  Long paymentId;

  @Column(nullable = false, length = 20)
  String type;

  @Column(nullable = false)
  int quantity;

  @Column(name = "available_before", nullable = false)
  int availableBefore;

  @Column(name = "available_after", nullable = false)
  int availableAfter;

  @Column(name = "reserved_before", nullable = false)
  int reservedBefore;

  @Column(name = "reserved_after", nullable = false)
  int reservedAfter;

  @Column(name = "source_id", insertable = false, updatable = false)
  Long sourceId;

  @Column(name = "created_at", nullable = false)
  LocalDateTime createdAt;
}

@Entity
@Table(name = "coupons")
class CouponEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false, length = 100)
  String name;

  @Column(name = "discount_type", nullable = false, length = 20)
  String discountType;

  @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
  BigDecimal discountValue;

  @Column(name = "minimum_order_amount", nullable = false, precision = 12, scale = 2)
  BigDecimal minimumOrderAmount;

  @Column(name = "maximum_discount_amount", precision = 12, scale = 2)
  BigDecimal maximumDiscountAmount;

  @Column(nullable = false)
  boolean active;
}

@Entity
@Table(name = "member_coupons")
class MemberCouponEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "member_id", nullable = false)
  Long memberId;

  @Column(name = "coupon_id", nullable = false)
  Long couponId;

  @Column(nullable = false, length = 20)
  String status;

  @Column(name = "reserved_order_id")
  Long reservedOrderId;

  @Column(name = "issued_at", nullable = false)
  LocalDateTime issuedAt;
}

@Entity
@Table(name = "membership_grades")
class MembershipGradeEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false, unique = true, length = 30)
  String code;

  @Column(nullable = false, length = 100)
  String name;

  @Column(name = "minimum_purchase_amount", nullable = false, precision = 18, scale = 2)
  BigDecimal minimumPurchaseAmount;

  @Column(name = "display_order", nullable = false)
  int displayOrder;

  @Column(nullable = false)
  boolean active;

  @Column(name = "benefit_coupon_id")
  Long benefitCouponId;
}

@Entity
@Table(name = "member_memberships")
class MemberMembershipEntity {
  @Id
  @Column(name = "member_id")
  Long memberId;

  @Column(name = "grade_id", nullable = false)
  Long gradeId;

  @Column(name = "evaluated_purchase_amount", nullable = false, precision = 18, scale = 2)
  BigDecimal evaluatedPurchaseAmount;

  @Column(name = "evaluated_at", nullable = false)
  LocalDateTime evaluatedAt;
}

@Entity
@Table(name = "membership_histories")
class MembershipHistoryEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "member_id", nullable = false)
  Long memberId;

  @Column(name = "from_grade_id")
  Long fromGradeId;

  @Column(name = "to_grade_id", nullable = false)
  Long toGradeId;

  @Column(name = "changed_at", nullable = false)
  LocalDateTime changedAt;
}

@Entity
@Table(name = "orders")
class CommerceOrderEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "order_number", nullable = false, unique = true, length = 80)
  String orderNumber;

  @Column(name = "member_id", nullable = false)
  Long memberId;

  @Column(nullable = false, length = 20)
  String source;

  @Column(nullable = false, length = 30)
  String status;

  @Column(name = "payment_amount", nullable = false, precision = 18, scale = 2)
  BigDecimal paymentAmount;

  @Column(name = "created_at", nullable = false)
  LocalDateTime createdAt;
}

@Entity
@Table(name = "order_items")
class CommerceOrderItemEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "order_id", nullable = false)
  Long orderId;

  @Column(name = "sku_id", nullable = false)
  Long skuId;

  @Column(nullable = false)
  int quantity;

  @Column(name = "line_amount", precision = 18, scale = 2)
  BigDecimal lineAmount;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", insertable = false, updatable = false)
  CommerceOrderEntity order;
}

@Entity
@Table(name = "payments")
class PaymentEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "order_id", nullable = false)
  Long orderId;

  @Column(nullable = false, length = 20)
  String type;

  @Column(nullable = false, length = 20)
  String provider;

  @Column(nullable = false, length = 20)
  String status;

  @Column(nullable = false, precision = 18, scale = 2)
  BigDecimal amount;

  @Column(name = "provider_order_id", nullable = false, unique = true, length = 100)
  String providerOrderId;

  @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
  String idempotencyKey;

  @Column(name = "attempt_no", nullable = false)
  int attemptNo;

  @Column(name = "reconciliation_attempts", nullable = false)
  int reconciliationAttempts;

  @Column(name = "succeeded_order_id", insertable = false, updatable = false)
  Long succeededOrderId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", insertable = false, updatable = false)
  CommerceOrderEntity order;
}

@Entity
@Table(name = "checkout_idempotency_results")
class CheckoutIdempotencyEntity {
  @EmbeddedId CheckoutIdempotencyId id;

  @Column(name = "order_id", nullable = false)
  Long orderId;

  @Column(name = "payment_id", nullable = false)
  Long paymentId;

  @Column(name = "request_fingerprint", length = 64)
  String requestFingerprint;

  @Column(name = "created_at", nullable = false)
  LocalDateTime createdAt;
}

@Entity
@Table(name = "billing_payment_methods")
class BillingPaymentMethodEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "member_id", nullable = false)
  Long memberId;

  @Column(nullable = false, length = 20)
  String provider;

  @Column(name = "customer_key", nullable = false, unique = true, length = 100)
  String customerKey;

  @Column(name = "billing_key", nullable = false, length = 300)
  String billingKey;

  @Column(nullable = false, length = 20)
  String status;

  @Column(name = "active_member_id", insertable = false, updatable = false)
  Long activeMemberId;
}

@Entity
@Table(name = "billing_payment_method_preparations")
class BillingPaymentMethodPreparationEntity {
  @Id
  @Column(name = "prepare_token", length = 100)
  String prepareToken;

  @Column(name = "member_id", nullable = false)
  Long memberId;

  @Column(name = "customer_key", nullable = false, length = 100)
  String customerKey;

  @Column(nullable = false, length = 20)
  String status;

  @Column(name = "expires_at", nullable = false)
  LocalDateTime expiresAt;

  @Column(name = "claimed_at")
  LocalDateTime claimedAt;
}

@Entity
@Table(name = "subscription_order_context")
class SubscriptionOrderContextEntity {
  @Id
  @Column(name = "order_id")
  Long orderId;

  @Column(name = "subscription_id", nullable = false)
  Long subscriptionId;

  @Column(name = "schedule_id", nullable = false, unique = true)
  Long scheduleId;

  @Column(name = "scheduled_date", nullable = false)
  java.time.LocalDate scheduledDate;
}

@Entity
@Table(name = "subscription_shipping_snapshots")
class SubscriptionShippingSnapshotEntity {
  @Id
  @Column(name = "subscription_id")
  Long subscriptionId;

  @Column(name = "recipient_name", nullable = false, length = 100)
  String recipientName;

  @Column(name = "updated_at", nullable = false)
  LocalDateTime updatedAt;
}

@Entity
@Table(name = "deliveries")
class DeliveryEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "order_id", nullable = false, unique = true)
  Long orderId;

  @Column(nullable = false, length = 20)
  String status;

  @Column(name = "carrier_code", length = 50)
  String carrierCode;

  @Column(name = "tracking_number", length = 100)
  String trackingNumber;
}

@Entity
@Table(name = "order_cancellations")
class OrderCancellationEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "order_id", nullable = false, unique = true)
  Long orderId;

  @Column(nullable = false, length = 20)
  String status;

  @Column(nullable = false, length = 500)
  String reason;

  @Column(name = "requested_at", nullable = false)
  LocalDateTime requestedAt;
}

@Entity
@Table(name = "order_returns")
class OrderReturnEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "order_id", nullable = false, unique = true)
  Long orderId;

  @Column(nullable = false, length = 20)
  String status;

  @Column(nullable = false, length = 500)
  String reason;

  @Column(name = "rejection_reason", length = 500)
  String rejectionReason;

  @Column(name = "requested_at", nullable = false)
  LocalDateTime requestedAt;
}

@Entity
@Table(name = "refunds")
class RefundEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "order_id", nullable = false)
  Long orderId;

  @Column(nullable = false, length = 20)
  String source;

  @Column(nullable = false, length = 20)
  String status;

  @Column(nullable = false, precision = 18, scale = 2)
  BigDecimal amount;

  @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
  String idempotencyKey;

  @Column(name = "attempt_no", nullable = false)
  int attemptNo;

  @Column(name = "source_id", insertable = false, updatable = false)
  Long sourceId;

  @Column(name = "succeeded_order_id", insertable = false, updatable = false)
  Long succeededOrderId;
}

@Entity
@Table(name = "notifications")
class NotificationEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "member_id", nullable = false)
  Long memberId;

  @Column(nullable = false, length = 40)
  String type;

  @Column(name = "reference_type", nullable = false, length = 40)
  String referenceType;

  @Column(name = "reference_id", nullable = false)
  Long referenceId;

  @Column(name = "created_at", nullable = false)
  LocalDateTime createdAt;
}

@Entity
@Table(name = "admin_audit_logs")
class AdminAuditLogEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "admin_id", nullable = false)
  Long adminId;

  @Column(nullable = false, length = 80)
  String action;

  @Column(name = "target_type", nullable = false, length = 40)
  String targetType;

  @Column(name = "target_id", nullable = false)
  Long targetId;

  @Column(name = "safe_detail_json", nullable = false, columnDefinition = "json")
  String safeDetailJson;

  @Column(name = "created_at", nullable = false)
  LocalDateTime createdAt;
}
