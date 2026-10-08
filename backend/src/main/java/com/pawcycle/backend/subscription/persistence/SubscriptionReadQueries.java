package com.pawcycle.backend.subscription.persistence;

import com.pawcycle.backend.subscription.persistence.projection.CommandHistoryProjection;
import com.pawcycle.backend.subscription.persistence.projection.NextDeliveryProjection;
import com.pawcycle.backend.subscription.persistence.projection.PageProjection;
import com.pawcycle.backend.subscription.persistence.projection.PendingSubscriptionChange;
import com.pawcycle.backend.subscription.persistence.projection.PetProjection;
import com.pawcycle.backend.subscription.persistence.projection.PlanVersionProjection;
import com.pawcycle.backend.subscription.persistence.projection.ScheduleAddonProjection;
import com.pawcycle.backend.subscription.persistence.projection.ScheduleViewProjection;
import com.pawcycle.backend.subscription.persistence.projection.SubscriptionItemDetailProjection;
import com.pawcycle.backend.subscription.persistence.projection.SubscriptionItemProjection;
import com.pawcycle.backend.subscription.persistence.projection.SubscriptionProjection;
import com.pawcycle.backend.subscription.persistence.projection.SubscriptionReadBatch;
import com.pawcycle.backend.subscription.persistence.projection.SubscriptionSnapshot;
import com.pawcycle.backend.subscription.persistence.projection.SubscriptionSnapshotBase;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** Typed reads shared by GET and command responses. The caller continues to own the transaction. */
@Component
class SubscriptionReadQueries {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private final SubscriptionReadRepository reads;

  SubscriptionReadQueries(SubscriptionReadRepository reads) {
    this.reads = reads;
  }

  Optional<PetProjection> ownedPet(long memberId, long petId) {
    return reads.ownedPet(memberId, petId);
  }

  Optional<PlanVersionProjection> planVersion(long id) {
    return reads.planVersion(id);
  }

  Optional<SubscriptionProjection> ownedSubscription(long memberId, long id) {
    return reads.ownedSubscription(memberId, id);
  }

  PageProjection<PetProjection> pets(long memberId, int page, int size) {
    long total = reads.petCount(memberId);
    return new PageProjection<>(page, size, total, reads.pets(memberId, PageRequest.of(page, size)));
  }

  PageProjection<PlanVersionProjection> salePlans(String petType, LocalDate today, int page, int size) {
    long total = reads.salePlanCount(petType, today);
    return new PageProjection<>(page, size, total,
        reads.salePlans(petType, today, PageRequest.of(page, size)));
  }

  Map<Long, List<SubscriptionItemProjection>> planItems(List<Long> ids) {
    return ids.isEmpty() ? Map.of() : groupedItems(reads.planItems(ids));
  }

  Map<Long, List<Integer>> cycles(List<Long> ids) {
    if (ids.isEmpty()) return Map.of();
    Map<Long, List<Integer>> result = new HashMap<>();
    for (var cycle : reads.cycles(ids)) {
      result.computeIfAbsent(cycle.versionId(), ignored -> new ArrayList<>()).add(cycle.weeks());
    }
    return result;
  }

  SubscriptionSnapshot snapshot(long id) {
    var base = reads.snapshots(List.of(id)).stream().findFirst().orElseThrow();
    return new SubscriptionSnapshot(base.id(), base.planVersionId(), base.packagePriceKrw(),
        base.deliveryCycleWeeks(), snapshotItems(List.of(id)).getOrDefault(id, List.of()));
  }

  PageProjection<SubscriptionProjection> subscriptions(long memberId, int page, int size) {
    long total = reads.subscriptionCount(memberId);
    return new PageProjection<>(page, size, total,
        reads.subscriptions(memberId, PageRequest.of(page, size)));
  }

  Map<Long, PetProjection> ownedPets(long memberId, List<Long> ids) {
    if (ids.isEmpty()) return Map.of();
    Map<Long, PetProjection> result = new HashMap<>();
    reads.ownedPets(memberId, ids).forEach(pet -> result.put(pet.id(), pet));
    return result;
  }

  Map<Long, SubscriptionSnapshotBase> snapshots(List<Long> ids) {
    if (ids.isEmpty()) return Map.of();
    Map<Long, SubscriptionSnapshotBase> result = new HashMap<>();
    reads.snapshots(ids).forEach(snapshot -> result.put(snapshot.id(), snapshot));
    return result;
  }

  Map<Long, List<SubscriptionItemProjection>> snapshotItems(List<Long> ids) {
    return ids.isEmpty() ? Map.of() : groupedItems(reads.snapshotItems(ids));
  }

  Map<Long, LocalDate> nextSchedules(List<Long> ids, LocalDate today) {
    if (ids.isEmpty()) return Map.of();
    Map<Long, LocalDate> result = new HashMap<>();
    reads.nextSchedules(ids, today).forEach(row -> result.putIfAbsent(row.subscriptionId(), row.date()));
    return result;
  }

  Optional<LocalDate> nextSchedule(long id, LocalDate today) {
    return reads.nextSchedule(id, today, PageRequest.of(0, 1)).stream().findFirst();
  }

  Optional<PendingSubscriptionChange> pendingChange(long id) {
    return reads.pendingChange(id);
  }

  Optional<NextDeliveryProjection> nextDelivery(long id) {
    return reads.nextDelivery(id, PageRequest.of(0, 1)).stream().findFirst();
  }

  List<SubscriptionItemDetailProjection> snapshotItemDetails(long id) {
    return reads.snapshotItemDetails(id);
  }

  List<ScheduleAddonProjection> addons(long id) {
    return reads.addons(id);
  }

  int addonCount(long id) {
    return reads.addonCount(id);
  }

  PageProjection<ScheduleViewProjection> schedules(long id, int page, int size) {
    long total = reads.scheduleCount(id);
    return new PageProjection<>(page, size, total, reads.schedules(id, PageRequest.of(page, size)));
  }

  PageProjection<CommandHistoryProjection> history(long id, int page, int size) {
    long total = reads.historyCount(id);
    return new PageProjection<>(page, size, total,
        reads.history(id, PageRequest.of(page, size)).stream()
            .map(row -> new CommandHistoryProjection(row.commandType(),
                row.occurredAt().toInstant().atZone(SEOUL).toOffsetDateTime().toString()))
            .toList());
  }

  private Map<Long, List<SubscriptionItemProjection>> groupedItems(List<SubscriptionReadBatch.Item> rows) {
    Map<Long, List<SubscriptionItemProjection>> result = new HashMap<>();
    for (var row : rows) {
      result.computeIfAbsent(row.ownerId(), ignored -> new ArrayList<>())
          .add(new SubscriptionItemProjection(row.skuId(), row.quantity()));
    }
    return result;
  }
}
