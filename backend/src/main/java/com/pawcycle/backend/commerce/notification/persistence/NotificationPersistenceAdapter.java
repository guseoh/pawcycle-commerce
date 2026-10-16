package com.pawcycle.backend.commerce.notification.persistence;

import com.pawcycle.backend.commerce.common.error.CommerceException;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.hibernate.FlushMode;
import org.hibernate.query.MutationQuery;
import org.hibernate.type.descriptor.java.JdbcTimestampJavaType;
import org.hibernate.type.internal.BasicTypeImpl;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationPersistenceAdapter {
  private static final BasicTypeImpl<Timestamp> LEGACY_TIMESTAMP_TYPE =
      new BasicTypeImpl<>(JdbcTimestampJavaType.INSTANCE, new NotificationTimestampJdbcType());
  private final EntityManager entities;
  private final Clock clock;

  public NotificationPersistenceAdapter(EntityManager entities, Clock clock) {
    this.entities = entities;
    this.clock = clock;
  }

  public void create(long memberId, String type, String referenceType, long referenceId) {
    mutation("INSERT INTO notifications(member_id,type,reference_type,reference_id,created_at) VALUES"
            + " (:member,:type,:referenceType,:reference,:created) ON DUPLICATE KEY UPDATE id=id")
        .setParameter("member", memberId).setParameter("type", type)
        .setParameter("referenceType", referenceType).setParameter("reference", referenceId)
        .setParameter("created", Timestamp.from(clock.instant()), timestampType()).executeUpdate();
  }

  public List<NotificationView> findByMemberId(long memberId) {
    return entities.unwrap(Session.class).createNativeQuery(
        "SELECT notification.id AS notificationId,notification.type,notification.reference_type"
            + " AS referenceType,notification.reference_id AS referenceId,notification.read_at"
            + " AS readAt,notification.created_at AS createdAt,schedule.subscription_id AS"
            + " subscriptionId,schedule.scheduled_date AS scheduledDate FROM notifications"
            + " notification LEFT JOIN subscription_schedules schedule ON"
            + " notification.type='SUBSCRIPTION_DELIVERY_REMINDER' AND"
            + " notification.reference_type='SCHEDULE' AND"
            + " schedule.id=notification.reference_id WHERE notification.member_id=:member ORDER BY"
            + " notification.id DESC",
        NotificationView.class)
        .addScalar("notificationId", Long.class).addScalar("type", String.class)
        .addScalar("referenceType", String.class).addScalar("referenceId", Long.class)
        .addScalar("readAt", timestampType()).addScalar("createdAt", timestampType())
        .addScalar("subscriptionId", Long.class).addScalar("scheduledDate", timestampType())
        .setHibernateFlushMode(FlushMode.COMMIT).setParameter("member", memberId).getResultList();
  }

  public void markRead(long memberId, long notificationId) {
    if (mutation("UPDATE notifications SET read_at=COALESCE(read_at,:read) WHERE id=:id AND member_id=:member")
        .setParameter("read", Timestamp.from(clock.instant()), timestampType())
        .setParameter("id", notificationId).setParameter("member", memberId).executeUpdate() != 1) {
      throw new CommerceException(404, "NOTIFICATION_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    }
  }

  public void markAllRead(long memberId) {
    mutation("UPDATE notifications SET read_at=:read WHERE member_id=:member AND read_at IS NULL")
        .setParameter("read", Timestamp.from(clock.instant()), timestampType())
        .setParameter("member", memberId).executeUpdate();
  }

  private MutationQuery mutation(String sql) {
    // JDBC did not flush pending entity changes before these independent atomic statements.
    return entities.unwrap(Session.class).createNativeMutationQuery(sql)
        .setHibernateFlushMode(FlushMode.COMMIT);
  }

  private static BasicTypeImpl<Timestamp> timestampType() {
    return LEGACY_TIMESTAMP_TYPE;
  }
}
