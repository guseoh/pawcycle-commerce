package com.pawcycle.backend.subscription.persistence;

import com.pawcycle.backend.commerce.common.error.CommerceException;
import jakarta.persistence.EntityManager;
import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.query.MutationQuery;
import org.hibernate.type.descriptor.java.JdbcTimestampJavaType;
import org.hibernate.type.internal.BasicTypeImpl;
import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.stereotype.Repository;

/** Owns the subscription tables touched by a member's shipping-address change. */
@Repository
public class SubscriptionShippingPersistenceAdapter {
  private final EntityManager entities;
  private final Clock clock;

  public SubscriptionShippingPersistenceAdapter(EntityManager entities, Clock clock) {
    this.entities = entities;
    this.clock = clock;
  }

  public void update(long memberId, long subscriptionId, ShippingAddress address) {
    if (entities.unwrap(Session.class).createNativeQuery(
            "SELECT COUNT(*) FROM subscriptions WHERE id=:subscription AND member_id=:member", Long.class)
        .setHibernateFlushMode(FlushMode.COMMIT).setParameter("subscription", subscriptionId)
        .setParameter("member", memberId).getSingleResult() != 1) {
      throw new CommerceException(404, "SUBSCRIPTION_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    }
    long future =
        entities.unwrap(Session.class).createNativeQuery(
            """
            SELECT COUNT(*) FROM subscription_schedules schedule
            LEFT JOIN subscription_order_context context ON context.schedule_id=schedule.id
            WHERE schedule.subscription_id=:subscription AND schedule.status IN ('SCHEDULED','HELD')
              AND context.order_id IS NULL
            """,
            Long.class).setHibernateFlushMode(FlushMode.COMMIT)
            .setParameter("subscription", subscriptionId).getSingleResult();
    if (future == 0) {
      throw new CommerceException(
          409, "SUBSCRIPTION_SHIPPING_NOT_CHANGEABLE", "변경 가능한 미래 Schedule이 없습니다.");
    }
    Timestamp now = Timestamp.from(clock.instant());
    mutation(
        """
        INSERT INTO subscription_shipping_snapshots(subscription_id,recipient_name,recipient_phone,postal_code,address_line1,address_line2,updated_at)
        VALUES (:subscription,:name,:phone,:postal,:line1,:line2,:now)
        ON DUPLICATE KEY UPDATE recipient_name=VALUES(recipient_name),recipient_phone=VALUES(recipient_phone),postal_code=VALUES(postal_code),address_line1=VALUES(address_line1),address_line2=VALUES(address_line2),updated_at=VALUES(updated_at)
        """)
        .setParameter("subscription", subscriptionId).setParameter("name", address.recipientName())
        .setParameter("phone", address.recipientPhone()).setParameter("postal", address.postalCode())
        .setParameter("line1", address.addressLine1()).setParameter("line2", address.addressLine2(), String.class)
        .setParameter("now", now, timestampType()).executeUpdate();
    mutation(
        """
        UPDATE subscription_schedules schedule
        LEFT JOIN subscription_order_context context ON context.schedule_id=schedule.id
        SET schedule.status='SCHEDULED',schedule.hold_reason=NULL
        WHERE schedule.subscription_id=:subscription AND schedule.status='HELD'
          AND schedule.hold_reason='MISSING_SHIPPING_ADDRESS' AND context.order_id IS NULL
        """).setParameter("subscription", subscriptionId).executeUpdate();
  }

  public void releaseAddressHolds(long memberId) {
    mutation(
        """
        UPDATE subscription_schedules schedule
        JOIN subscriptions subscription ON subscription.id=schedule.subscription_id
        JOIN members member ON member.id=subscription.member_id
        LEFT JOIN subscription_shipping_snapshots snapshot ON snapshot.subscription_id=subscription.id
        LEFT JOIN subscription_order_context context ON context.schedule_id=schedule.id
        SET schedule.status='SCHEDULED',schedule.hold_reason=NULL
        WHERE subscription.member_id=:member AND member.default_address_id IS NOT NULL
          AND subscription.status='ACTIVE' AND snapshot.subscription_id IS NULL
          AND schedule.status='HELD' AND schedule.hold_reason='MISSING_SHIPPING_ADDRESS'
          AND context.order_id IS NULL
        """).setParameter("member", memberId).executeUpdate();
  }

  private MutationQuery mutation(String sql) {
    // Preserve JDBC visibility: the address service owns its explicit saveAndFlush calls.
    return entities.unwrap(Session.class).createNativeMutationQuery(sql)
        .setHibernateFlushMode(FlushMode.COMMIT);
  }

  // Shared descriptors are stateless and preserve the existing JDBC Timestamp binder.
  private static final BasicTypeImpl<Timestamp> LEGACY_TIMESTAMP_TYPE =
      new BasicTypeImpl<>(JdbcTimestampJavaType.INSTANCE, new SubscriptionShippingTimestampJdbcType());

  private static BasicTypeImpl<Timestamp> timestampType() {
    return LEGACY_TIMESTAMP_TYPE;
  }

  public record ShippingAddress(
      String recipientName,
      String recipientPhone,
      String postalCode,
      String addressLine1,
      String addressLine2) {}
}
