package com.pawcycle.backend.interaction.persistence;

import java.sql.Timestamp;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import org.hibernate.Session;
import org.hibernate.FlushMode;
import org.hibernate.type.descriptor.java.JdbcTimestampJavaType;
import org.hibernate.type.internal.BasicTypeImpl;
import org.springframework.stereotype.Component;

@Component
public class InteractionEventPersistenceAdapter {
  private static final BasicTypeImpl<Timestamp> LEGACY_TIMESTAMP_TYPE =
      new BasicTypeImpl<>(JdbcTimestampJavaType.INSTANCE, new InteractionTimestampJdbcType());
  private final EntityManager entities;

  InteractionEventPersistenceAdapter(EntityManager entities) {
    this.entities = entities;
  }

  public boolean petBelongsToMember(long petId, long memberId) {
    return entities.createQuery("select count(p) from SubReadPet p where p.id=:pet and p.memberId=:member", Long.class)
        .setFlushMode(FlushModeType.COMMIT).setParameter("pet", petId).setParameter("member", memberId)
        .getSingleResult() == 1;
  }

  public boolean productExists(long productId) {
    return entities.createQuery("select count(p) from Product p where p.id=:product", Long.class)
        .setFlushMode(FlushModeType.COMMIT).setParameter("product", productId).getSingleResult() == 1;
  }

  public void insert(InteractionRecord event) {
    entities.unwrap(Session.class).createNativeMutationQuery(
        """
        INSERT INTO interaction_events(member_id,event_id,event_type,product_id,pet_id,source,recommendation_request_id,context,occurred_at)
        VALUES (:member,:event,:type,:product,:pet,:source,:request,:context,:occurred) ON DUPLICATE KEY UPDATE id=id
        """)
        .setHibernateFlushMode(FlushMode.COMMIT)
        .setParameter("member", event.memberId())
        .setParameter("event", event.eventId())
        .setParameter("type", event.eventType())
        .setParameter("product", event.productId(), Long.class)
        .setParameter("pet", event.petId(), Long.class)
        .setParameter("source", event.source(), String.class)
        .setParameter("request", event.recommendationRequestId(), String.class)
        .setParameter("context", event.contextJson(), String.class)
        .setParameter("occurred", event.occurredAt(), LEGACY_TIMESTAMP_TYPE)
        .executeUpdate();
  }

  public record InteractionRecord(
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
