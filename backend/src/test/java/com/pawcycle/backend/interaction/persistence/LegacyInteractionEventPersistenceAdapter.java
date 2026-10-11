package com.pawcycle.backend.interaction.persistence;

import com.pawcycle.backend.interaction.persistence.InteractionEventPersistenceAdapter.InteractionRecord;
import org.springframework.jdbc.core.JdbcTemplate;

final class LegacyInteractionEventPersistenceAdapter {
  private final JdbcTemplate jdbc;

  LegacyInteractionEventPersistenceAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean petBelongsToMember(long petId, long memberId) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM pets WHERE id=? AND member_id=?", Integer.class, petId, memberId)
        == 1;
  }

  public boolean productExists(long productId) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id=?", Integer.class, productId)
        == 1;
  }

  public void insert(InteractionRecord event) {
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

}
