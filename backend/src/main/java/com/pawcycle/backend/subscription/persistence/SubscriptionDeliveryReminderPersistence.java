package com.pawcycle.backend.subscription.persistence;

import java.time.LocalDate;
import java.util.List;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.hibernate.FlushMode;
import org.hibernate.type.descriptor.java.LocalDateJavaType;
import org.hibernate.type.descriptor.jdbc.LocalDateJdbcType;
import org.hibernate.type.internal.BasicTypeImpl;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionDeliveryReminderPersistence {
  private final EntityManager entities;

  public SubscriptionDeliveryReminderPersistence(EntityManager entities) {
    this.entities = entities;
  }

  public List<ReminderTarget> findEligible(LocalDate today, LocalDate through) {
    return entities.unwrap(Session.class).createNativeQuery(
        "SELECT subscription.member_id,schedule.id schedule_id FROM subscription_schedules"
            + " schedule JOIN subscriptions subscription ON"
            + " subscription.id=schedule.subscription_id WHERE subscription.status='ACTIVE' AND"
            + " subscription.runtime_managed=true AND schedule.status='SCHEDULED' AND"
            + " schedule.scheduled_date>:today AND schedule.scheduled_date<=:through AND NOT EXISTS (SELECT"
            + " 1 FROM subscription_orders existing_order WHERE"
            + " existing_order.schedule_id=schedule.id)",
        ReminderTarget.class)
        .addScalar("member_id", Long.class).addScalar("schedule_id", Long.class)
        .setHibernateFlushMode(FlushMode.COMMIT)
        .setParameter("today", today, dateType()).setParameter("through", through, dateType())
        .getResultList();
  }

  public int deleteIneligible(LocalDate today, LocalDate through) {
    return entities.unwrap(Session.class).createNativeMutationQuery(
        "DELETE FROM notifications WHERE type='SUBSCRIPTION_DELIVERY_REMINDER' AND"
            + " reference_type='SCHEDULE' AND NOT EXISTS (SELECT 1 FROM subscription_schedules"
            + " schedule JOIN subscriptions subscription ON"
            + " subscription.id=schedule.subscription_id WHERE"
            + " schedule.id=notifications.reference_id AND subscription.status='ACTIVE' AND"
            + " subscription.runtime_managed=true AND schedule.status='SCHEDULED' AND"
            + " schedule.scheduled_date>:today AND schedule.scheduled_date<=:through AND NOT EXISTS (SELECT 1"
            + " FROM subscription_orders existing_order WHERE"
            + " existing_order.schedule_id=schedule.id))")
        .setHibernateFlushMode(FlushMode.COMMIT)
        .setParameter("today", today, dateType()).setParameter("through", through, dateType())
        .executeUpdate();
  }

  private static BasicTypeImpl<LocalDate> dateType() {
    // Match JDBC 4.2 LocalDate binding, without Hibernate's global UTC Calendar conversion.
    return new BasicTypeImpl<>(LocalDateJavaType.INSTANCE, LocalDateJdbcType.INSTANCE);
  }

  public record ReminderTarget(long memberId, long scheduleId) {}
}
