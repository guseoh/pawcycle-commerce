package com.pawcycle.backend.subscription.persistence.projection;

import java.sql.Timestamp;
import java.time.LocalDate;

/** Typed keys and values for batched scalar queries; never expose persistence entities. */
public final class SubscriptionReadBatch {
  private SubscriptionReadBatch() {}

  public record Item(long ownerId, long skuId, int quantity) {}
  public record Cycle(long versionId, int weeks) {}
  public record NextSchedule(long subscriptionId, LocalDate date) {}
  public record History(String commandType, Timestamp occurredAt) {}
}
