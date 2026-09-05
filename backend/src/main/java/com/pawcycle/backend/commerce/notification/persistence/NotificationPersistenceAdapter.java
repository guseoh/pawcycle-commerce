package com.pawcycle.backend.commerce.notification.persistence;

import com.pawcycle.backend.commerce.CommerceException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationPersistenceAdapter {
  private final JdbcTemplate queries;
  private final Clock clock;

  public NotificationPersistenceAdapter(JdbcTemplate queries, Clock clock) {
    this.queries = queries;
    this.clock = clock;
  }

  public void create(long memberId, String type, String referenceType, long referenceId) {
    queries.update(
        "INSERT INTO notifications(member_id,type,reference_type,reference_id,created_at) VALUES"
            + " (?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",
        memberId,
        type,
        referenceType,
        referenceId,
        Timestamp.from(clock.instant()));
  }

  public List<NotificationView> findByMemberId(long memberId) {
    return queries.query(
        "SELECT notification.id AS notificationId,notification.type,notification.reference_type"
            + " AS referenceType,notification.reference_id AS referenceId,notification.read_at"
            + " AS readAt,notification.created_at AS createdAt,schedule.subscription_id AS"
            + " subscriptionId,schedule.scheduled_date AS scheduledDate FROM notifications"
            + " notification LEFT JOIN subscription_schedules schedule ON"
            + " notification.type='SUBSCRIPTION_DELIVERY_REMINDER' AND"
            + " notification.reference_type='SCHEDULE' AND"
            + " schedule.id=notification.reference_id WHERE notification.member_id=? ORDER BY"
            + " notification.id DESC",
        (rs, rowNumber) -> {
          long subscriptionId = rs.getLong("subscriptionId");
          boolean subscriptionMissing = rs.wasNull();
          return new NotificationView(
              rs.getLong("notificationId"),
              rs.getString("type"),
              rs.getString("referenceType"),
              rs.getLong("referenceId"),
              rs.getTimestamp("readAt"),
              rs.getTimestamp("createdAt"),
              subscriptionMissing ? null : subscriptionId,
              rs.getTimestamp("scheduledDate"));
        },
        memberId);
  }

  public void markRead(long memberId, long notificationId) {
    if (queries.update(
            "UPDATE notifications SET read_at=COALESCE(read_at,?) WHERE id=? AND member_id=?",
            Timestamp.from(clock.instant()),
            notificationId,
            memberId)
        != 1) {
      throw new CommerceException(404, "NOTIFICATION_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    }
  }

  public void markAllRead(long memberId) {
    queries.update(
        "UPDATE notifications SET read_at=? WHERE member_id=? AND read_at IS NULL",
        Timestamp.from(clock.instant()),
        memberId);
  }
}
