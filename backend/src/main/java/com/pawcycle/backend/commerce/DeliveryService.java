package com.pawcycle.backend.commerce;

import com.pawcycle.backend.commerce.delivery.api.DeliveryResponse;
import com.pawcycle.backend.commerce.delivery.persistence.DeliveryPersistenceAdapter;
import com.pawcycle.backend.commerce.delivery.persistence.DeliveryView;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Delivery state transitions are owned by this application service. */
@Service
public class DeliveryService {
  private final DeliveryPersistenceAdapter deliveries;
  private final TransactionTemplate transaction;
  private final NotificationService notifications;
  private final AdminAuditService audits;
  private final CommerceMetrics metrics;

  public DeliveryService(
      DeliveryPersistenceAdapter deliveries,
      org.springframework.transaction.PlatformTransactionManager transactionManager,
      NotificationService notifications,
      AdminAuditService audits,
      CommerceMetrics metrics) {
    this.deliveries = deliveries;
    this.transaction = new TransactionTemplate(transactionManager);
    this.notifications = notifications;
    this.audits = audits;
    this.metrics = metrics;
  }

  /** Called from the successful-payment transaction; duplicate callbacks keep the one delivery. */
  public void createPreparing(long orderId) {
    deliveries.createPreparing(orderId);
  }

  public DeliveryResponse ship(long id, String carrier, String tracking) {
    return ship(null, id, carrier, tracking);
  }

  public DeliveryResponse ship(Long adminId, long id, String carrier, String tracking) {
    Timer.Sample sample = metrics.timer();
    try {
      return transaction.execute(
          status -> {
            DeliveryPersistenceAdapter.DeliveryLock row = requireDelivery(id);
            if (!"PREPARING".equals(row.status()) && !"FAILED".equals(row.status())) {
              throw new CommerceException(409, "DELIVERY_STATE_CONFLICT", "배송 상태를 전이할 수 없습니다.");
            }
            deliveries.ship(id, carrier, tracking);
            long memberId = deliveries.memberId(row.orderId());
            notifications.create(memberId, "ORDER_SHIPPED", "DELIVERY", id);
            metrics.count("delivery.transition", "SHIPPED");
            if (adminId != null) audits.append(adminId, "DELIVERY_SHIP", "DELIVERY", id);
            return response(id);
          });
    } finally {
      metrics.stop(sample, "delivery.transition");
    }
  }

  public DeliveryResponse complete(long id) {
    return complete(null, id);
  }

  public DeliveryResponse complete(Long adminId, long id) {
    return transition(adminId, id, "SHIPPED", "DELIVERED", null, "ORDER_DELIVERED", "DELIVERY_COMPLETE");
  }

  public DeliveryResponse fail(long id, String reason) {
    return fail(null, id, reason);
  }

  public DeliveryResponse fail(Long adminId, long id, String reason) {
    return transition(adminId, id, "SHIPPED", "FAILED", reason, null, "DELIVERY_FAIL");
  }

  private DeliveryResponse transition(
      Long adminId,
      long id,
      String from,
      String to,
      String failure,
      String notification,
      String auditAction) {
    Timer.Sample sample = metrics.timer();
    try {
      return transaction.execute(
          status -> {
            DeliveryPersistenceAdapter.DeliveryLock row = requireDelivery(id);
            if (!from.equals(row.status())) {
              throw new CommerceException(409, "DELIVERY_STATE_CONFLICT", "배송 상태를 전이할 수 없습니다.");
            }
            deliveries.transition(id, from, to, failure);
            if (notification != null) {
              notifications.create(
                  deliveries.memberId(row.orderId()), notification, "DELIVERY", id);
            }
            metrics.count("delivery.transition", to);
            if (adminId != null) audits.append(adminId, auditAction, "DELIVERY", id);
            return response(id);
          });
    } finally {
      metrics.stop(sample, "delivery.transition");
    }
  }

  private DeliveryPersistenceAdapter.DeliveryLock requireDelivery(long id) {
    DeliveryPersistenceAdapter.DeliveryLock row = deliveries.findForUpdate(id);
    if (row == null) {
      throw new CommerceException(404, "DELIVERY_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    }
    return row;
  }

  private DeliveryResponse response(long id) {
    DeliveryView view = deliveries.find(id);
    if (view == null) {
      throw new CommerceException(404, "DELIVERY_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    }
    return new DeliveryResponse(
        view.deliveryId(),
        view.orderId(),
        view.status(),
        view.carrierCode(),
        view.trackingNumber(),
        view.failureReason(),
        view.shippedAt(),
        view.deliveredAt(),
        view.failedAt(),
        view.cancelledAt());
  }
}
