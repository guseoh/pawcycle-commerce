package com.pawcycle.backend.commerce;

import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists only in-app notifications; the event key makes transaction replay harmless. */
@Service
public class NotificationService {
  private final NativeQueryExecutor jdbc;
  private final Clock clock;

  public NotificationService(NativeQueryExecutor jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Transactional
  public void create(long memberId, String type, String referenceType, long referenceId) {
    jdbc.update(
        "INSERT INTO notifications(member_id,type,reference_type,reference_id,created_at) VALUES"
            + " (?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",
        memberId,
        type,
        referenceType,
        referenceId,
        Timestamp.from(clock.instant()));
  }

  @Transactional(readOnly = true)
  public List<CommerceRowResponse> list(long memberId) {
    List<Map<String, Object>> result =
        jdbc.queryForList(
            "SELECT notification.id AS notificationId,notification.type,notification.reference_type"
                + " AS referenceType,notification.reference_id AS referenceId,notification.read_at"
                + " AS readAt,notification.created_at AS createdAt,schedule.subscription_id AS"
                + " subscriptionId,schedule.scheduled_date AS scheduledDate FROM notifications"
                + " notification LEFT JOIN subscription_schedules schedule ON"
                + " notification.type='SUBSCRIPTION_DELIVERY_REMINDER' AND"
                + " notification.reference_type='SCHEDULE' AND"
                + " schedule.id=notification.reference_id WHERE notification.member_id=? ORDER BY"
                + " notification.id DESC",
            memberId);
    result.forEach(
        item -> {
          if (item.get("subscriptionId") == null) {
            item.remove("subscriptionId");
            item.remove("scheduledDate");
          }
        });
    return CommerceRowResponse.from(result);
  }

  @Transactional
  public void read(long memberId, long id) {
    if (jdbc.update(
            "UPDATE notifications SET read_at=COALESCE(read_at,?) WHERE id=? AND member_id=?",
            Timestamp.from(clock.instant()),
            id,
            memberId)
        != 1) throw new CommerceException(404, "NOTIFICATION_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
  }

  @Transactional
  public void readAll(long memberId) {
    jdbc.update(
        "UPDATE notifications SET read_at=? WHERE member_id=? AND read_at IS NULL",
        Timestamp.from(clock.instant()),
        memberId);
  }
}
