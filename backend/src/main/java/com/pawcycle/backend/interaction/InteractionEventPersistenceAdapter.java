package com.pawcycle.backend.interaction;

import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class InteractionEventPersistenceAdapter {
  private final JdbcTemplate jdbc;

  InteractionEventPersistenceAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  boolean petBelongsToMember(long petId, long memberId) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM pets WHERE id=? AND member_id=?", Integer.class, petId, memberId)
        == 1;
  }

  boolean productExists(long productId) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id=?", Integer.class, productId)
        == 1;
  }

  void insert(InteractionRecord event) {
    jdbc.update(
        """
        INSERT INTO interaction_events(member_id,event_id,event_type,product_id,pet_id,source,recommendation_request_id,context,occurred_at)
        VALUES (?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id
        """,
        event.memberId(),
        event.eventId(),
        event.eventType(),
        event.productId(),
        event.petId(),
        event.source(),
        event.recommendationRequestId(),
        event.contextJson(),
        event.occurredAt());
  }

  record InteractionRecord(
      long memberId,
      String eventId,
      String eventType,
      Long productId,
      Long petId,
      String source,
      String recommendationRequestId,
      String contextJson,
      Timestamp occurredAt) {}
}
