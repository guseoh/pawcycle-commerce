package com.pawcycle.backend.commerce;

import com.pawcycle.backend.commerce.notification.api.NotificationResponse;
import com.pawcycle.backend.commerce.notification.persistence.NotificationPersistenceAdapter;
import com.pawcycle.backend.commerce.notification.persistence.NotificationView;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application boundary for notification use cases; SQL ownership stays in the notification adapter. */
@Service
public class NotificationService {
  private final NotificationPersistenceAdapter notifications;

  public NotificationService(NotificationPersistenceAdapter notifications) {
    this.notifications = notifications;
  }

  @Transactional
  public void create(long memberId, String type, String referenceType, long referenceId) {
    notifications.create(memberId, type, referenceType, referenceId);
  }

  @Transactional(readOnly = true)
  public List<NotificationResponse> list(long memberId) {
    return notifications.findByMemberId(memberId).stream()
        .map(NotificationService::response)
        .toList();
  }

  @Transactional
  public void read(long memberId, long id) {
    notifications.markRead(memberId, id);
  }

  @Transactional
  public void readAll(long memberId) {
    notifications.markAllRead(memberId);
  }

  private static NotificationResponse response(NotificationView view) {
    return new NotificationResponse(
        view.notificationId(),
        view.type(),
        view.referenceType(),
        view.referenceId(),
        view.readAt(),
        view.createdAt(),
        view.subscriptionId(),
        view.scheduledDate());
  }
}
