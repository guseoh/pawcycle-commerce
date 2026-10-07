package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CommerceTopologyIntegrationTests {
  @Autowired private EntityManagerFactory entities;
  @Autowired private ApplicationContext context;

  @Test
  void discoversTheSameTwentyFourCommerceEntityMappings() {
    Set<String> expected = Set.of(
        "AdminAuditLogEntity", "BillingPaymentMethodEntity", "BillingPaymentMethodPreparationEntity",
        "CartEntity", "CartItemEntity", "CheckoutIdempotencyEntity", "CommerceOrderEntity",
        "CommerceOrderItemEntity", "CouponEntity", "DeliveryEntity", "InventoryEntity",
        "InventoryMovementEntity", "MemberCouponEntity", "MemberMembershipEntity",
        "MembershipGradeEntity", "MembershipHistoryEntity", "NotificationEntity",
        "OrderCancellationEntity", "OrderReturnEntity", "PaymentEntity", "RefundEntity",
        "SubscriptionOrderContextEntity", "SubscriptionShippingSnapshotEntity", "WishlistItemEntity");
    assertThat(entities.getMetamodel().getEntities().stream()
        .map(type -> type.getJavaType())
        .filter(type -> type.getName().startsWith("com.pawcycle.backend.commerce."))
        .map(Class::getSimpleName).toList()).containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  void discoversExactlyOneBeanForEachExistingCommerceJpaRepository() throws Exception {
    // Repository inventory captured at main 873f144 before the package move.
    Set<String> repositories = Set.of(
        "audit.persistence.AdminAuditLogRepository",
        "billing.persistence.BillingPaymentMethodPreparationRepository",
        "billing.persistence.BillingPaymentMethodRepository",
        "cart.persistence.CartItemRepository", "cart.persistence.CartRepository",
        "checkout.persistence.CheckoutIdempotencyRepository",
        "coupon.persistence.CouponRepository", "coupon.persistence.MemberCouponRepository",
        "delivery.persistence.DeliveryRepository",
        "inventory.persistence.InventoryMovementRepository", "inventory.persistence.InventoryRepository",
        "notification.persistence.NotificationRepository", "order.persistence.CommerceOrderRepository",
        "order.persistence.OrderItemRepository", "payment.persistence.PaymentRepository",
        "refund.persistence.RefundRepository", "returning.persistence.OrderReturnRepository",
        "wishlist.persistence.WishlistItemRepository");
    for (String name : repositories) {
      assertThat(context.getBeansOfType(Class.forName("com.pawcycle.backend.commerce." + name)))
          .as("Repository discovery: %s", name).hasSize(1);
    }
    assertThat(AopUtils.getTargetClass(context.getBean("checkoutExpirationProcessor")).getName())
        .isEqualTo("com.pawcycle.backend.commerce.checkout.application.CheckoutExpirationProcessor");
    assertThat(AopUtils.getTargetClass(context.getBean("commerceExceptionHandler")).getName())
        .isEqualTo("com.pawcycle.backend.commerce.common.api.CommerceExceptionHandler");
  }
}
