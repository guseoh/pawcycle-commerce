package com.pawcycle.backend.commerce;

import com.pawcycle.backend.commerce.checkout.persistence.CheckoutExpirationPersistenceAdapter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Per-payment transaction boundary for expiry traversal. */
@Service
class CheckoutExpirationProcessor {
  private final CheckoutExpirationPersistenceAdapter expirations;
  private final InventoryService inventory;

  CheckoutExpirationProcessor(
      CheckoutExpirationPersistenceAdapter expirations, InventoryService inventory) {
    this.expirations = expirations;
    this.inventory = inventory;
  }

  @Transactional
  public boolean expire(long paymentId) {
    CheckoutExpirationPersistenceAdapter.ExpirationTarget payment =
        expirations.findForUpdate(paymentId);
    if (payment == null
        || !"READY".equals(payment.paymentStatus())
        || !"PAYMENT_PENDING".equals(payment.orderStatus())) return false;
    for (CheckoutExpirationPersistenceAdapter.CheckoutCartItem item : expirations.findOrderItems(payment.orderId())) {
      inventory.release(item.skuId(), item.quantity(), paymentId);
    }
    expirations.releaseCoupon(payment.orderId());
    expirations.markExpired(paymentId, payment.orderId());
    return true;
  }
}
