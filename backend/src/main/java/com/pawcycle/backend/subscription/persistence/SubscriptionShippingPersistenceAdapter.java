package com.pawcycle.backend.subscription.persistence;

import com.pawcycle.backend.commerce.CommerceException;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.stereotype.Repository;

/** Owns the subscription tables touched by a member's shipping-address change. */
@Repository
public class SubscriptionShippingPersistenceAdapter {
  private final NativeQueryExecutor queries;
  private final Clock clock;

  public SubscriptionShippingPersistenceAdapter(NativeQueryExecutor queries, Clock clock) {
    this.queries = queries;
    this.clock = clock;
  }

  public void update(long memberId, long subscriptionId, ShippingAddress address) {
    if (queries.queryForObject(
            "SELECT COUNT(*) FROM subscriptions WHERE id=? AND member_id=?",
            Integer.class,
            subscriptionId,
            memberId)
        != 1) {
      throw new CommerceException(404, "SUBSCRIPTION_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    }
    int future =
        queries.queryForObject(
            """
            SELECT COUNT(*) FROM subscription_schedules schedule
            LEFT JOIN subscription_order_context context ON context.schedule_id=schedule.id
            WHERE schedule.subscription_id=? AND schedule.status IN ('SCHEDULED','HELD')
              AND context.order_id IS NULL
            """,
            Integer.class,
            subscriptionId);
    if (future == 0) {
      throw new CommerceException(
          409, "SUBSCRIPTION_SHIPPING_NOT_CHANGEABLE", "변경 가능한 미래 Schedule이 없습니다.");
    }
    Timestamp now = Timestamp.from(clock.instant());
    queries.update(
        """
        INSERT INTO subscription_shipping_snapshots(subscription_id,recipient_name,recipient_phone,postal_code,address_line1,address_line2,updated_at)
        VALUES (?,?,?,?,?,?,?)
        ON DUPLICATE KEY UPDATE recipient_name=VALUES(recipient_name),recipient_phone=VALUES(recipient_phone),postal_code=VALUES(postal_code),address_line1=VALUES(address_line1),address_line2=VALUES(address_line2),updated_at=VALUES(updated_at)
        """,
        subscriptionId,
        address.recipientName(),
        address.recipientPhone(),
        address.postalCode(),
        address.addressLine1(),
        address.addressLine2(),
        now);
    queries.update(
        """
        UPDATE subscription_schedules schedule
        LEFT JOIN subscription_order_context context ON context.schedule_id=schedule.id
        SET schedule.status='SCHEDULED',schedule.hold_reason=NULL
        WHERE schedule.subscription_id=? AND schedule.status='HELD'
          AND schedule.hold_reason='MISSING_SHIPPING_ADDRESS' AND context.order_id IS NULL
        """,
        subscriptionId);
  }

  public void releaseAddressHolds(long memberId) {
    queries.update(
        """
        UPDATE subscription_schedules schedule
        JOIN subscriptions subscription ON subscription.id=schedule.subscription_id
        JOIN members member ON member.id=subscription.member_id
        LEFT JOIN subscription_shipping_snapshots snapshot ON snapshot.subscription_id=subscription.id
        LEFT JOIN subscription_order_context context ON context.schedule_id=schedule.id
        SET schedule.status='SCHEDULED',schedule.hold_reason=NULL
        WHERE subscription.member_id=? AND member.default_address_id IS NOT NULL
          AND subscription.status='ACTIVE' AND snapshot.subscription_id IS NULL
          AND schedule.status='HELD' AND schedule.hold_reason='MISSING_SHIPPING_ADDRESS'
          AND context.order_id IS NULL
        """,
        memberId);
  }

  public record ShippingAddress(
      String recipientName,
      String recipientPhone,
      String postalCode,
      String addressLine1,
      String addressLine2) {}
}
