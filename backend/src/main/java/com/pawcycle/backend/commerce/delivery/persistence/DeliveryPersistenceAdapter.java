package com.pawcycle.backend.commerce.delivery.persistence;

import com.pawcycle.backend.commerce.CommerceOrderRepository;
import com.pawcycle.backend.commerce.DeliveryEntity;
import com.pawcycle.backend.commerce.DeliveryRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class DeliveryPersistenceAdapter {
  private final DeliveryRepository deliveries;
  private final CommerceOrderRepository orders;
  private final Clock clock;

  public DeliveryPersistenceAdapter(
      DeliveryRepository deliveries, CommerceOrderRepository orders, Clock clock) {
    this.deliveries = deliveries;
    this.orders = orders;
    this.clock = clock;
  }

  @Transactional
  public void createPreparing(long orderId) {
    deliveries.insertPreparing(orderId);
  }

  @Transactional
  public DeliveryLock findForUpdate(long deliveryId) {
    return deliveries
        .findByIdForUpdate(deliveryId)
        .map(delivery -> new DeliveryLock(delivery.getId(), delivery.getOrderId(), delivery.getStatus()))
        .orElse(null);
  }

  @Transactional(readOnly = true)
  public long memberId(long orderId) {
    return orders.findById(orderId).map(order -> order.getMemberId()).orElseThrow();
  }

  @Transactional
  public void ship(long deliveryId, String carrier, String tracking) {
    deliveries.findById(deliveryId).ifPresent(delivery -> delivery.ship(carrier, tracking, now()));
  }

  @Transactional
  public void transition(long deliveryId, String from, String to, String failureReason) {
    deliveries
        .findById(deliveryId)
        .ifPresent(delivery -> delivery.transition(to, failureReason, now()));
  }

  @Transactional(readOnly = true)
  public DeliveryView find(long deliveryId) {
    return deliveries.findById(deliveryId).map(this::view).orElse(null);
  }

  private DeliveryView view(DeliveryEntity delivery) {
    return new DeliveryView(
        delivery.getId(),
        delivery.getOrderId(),
        delivery.getStatus(),
        delivery.getCarrierCode(),
        delivery.getTrackingNumber(),
        delivery.getFailureReason(),
        timestamp(delivery.getShippedAt()),
        timestamp(delivery.getDeliveredAt()),
        timestamp(delivery.getFailedAt()),
        timestamp(delivery.getCancelledAt()));
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault());
  }

  private Timestamp timestamp(LocalDateTime value) {
    return value == null ? null : Timestamp.valueOf(value);
  }

  public record DeliveryLock(long deliveryId, long orderId, String status) {}
}
