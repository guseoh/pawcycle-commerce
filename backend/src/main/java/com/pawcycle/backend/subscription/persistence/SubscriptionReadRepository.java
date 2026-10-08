package com.pawcycle.backend.subscription.persistence;

import com.pawcycle.backend.subscription.persistence.projection.NextDeliveryProjection;
import com.pawcycle.backend.subscription.persistence.projection.PendingSubscriptionChange;
import com.pawcycle.backend.subscription.persistence.projection.PetProjection;
import com.pawcycle.backend.subscription.persistence.projection.PlanVersionProjection;
import com.pawcycle.backend.subscription.persistence.projection.ScheduleAddonProjection;
import com.pawcycle.backend.subscription.persistence.projection.ScheduleViewProjection;
import com.pawcycle.backend.subscription.persistence.projection.SubscriptionItemDetailProjection;
import com.pawcycle.backend.subscription.persistence.projection.SubscriptionProjection;
import com.pawcycle.backend.subscription.persistence.projection.SubscriptionReadBatch;
import com.pawcycle.backend.subscription.persistence.projection.SubscriptionSnapshotBase;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Spring Data query-only boundary. All results are scalar constructor projections, never entities. */
interface SubscriptionReadRepository extends Repository<SubscriptionReadRows.Subscription, Long> {
  String PET = "select new com.pawcycle.backend.subscription.persistence.projection.PetProjection"
      + "(p.id, p.name, p.petType, p.breed, p.weightKg) from SubReadPet p ";
  String PLAN = "select new com.pawcycle.backend.subscription.persistence.projection.PlanVersionProjection"
      + "(p.id, p.name, p.currentPlanVersionId, p.targetPetType, p.onSale, p.saleStartsOn, p.saleEndsOn,"
      + " v.id, v.packagePriceKrw, v.migrationOnly) ";
  String SALE = "from SubReadPlan p join SubReadPlanVersion v on v.id=p.currentPlanVersionId"
      + " where p.name is not null and p.targetPetType=:petType and p.onSale=true"
      + " and v.migrationOnly=false and (p.saleStartsOn is null or p.saleStartsOn<=:today)"
      + " and (p.saleEndsOn is null or p.saleEndsOn>=:today) ";
  String SUBSCRIPTION = "select new com.pawcycle.backend.subscription.persistence.projection.SubscriptionProjection"
      + "(s.id, s.memberId, s.status, s.version, s.petId, s.deliveryCycleWeeks, s.currentSnapshotId)"
      + " from SubReadSubscription s ";
  String SNAPSHOT = "select new com.pawcycle.backend.subscription.persistence.projection.SubscriptionSnapshotBase"
      + "(s.id, s.planVersionId, s.packagePriceKrw, s.deliveryCycleWeeks) from SubReadSnapshot s ";
  String UNORDERED = "not exists (select o.id from SubReadOrder o where o.scheduleId=s.id)";

  @Query(PET + "where p.memberId=:memberId and p.id=:petId")
  Optional<PetProjection> ownedPet(@Param("memberId") long memberId, @Param("petId") long petId);

  @Query(PET + "where p.memberId=:memberId order by p.id")
  List<PetProjection> pets(@Param("memberId") long memberId, Pageable page);

  @Query("select count(p) from SubReadPet p where p.memberId=:memberId")
  long petCount(@Param("memberId") long memberId);

  @Query(PET + "where p.memberId=:memberId and p.id in :ids")
  List<PetProjection> ownedPets(@Param("memberId") long memberId, @Param("ids") List<Long> ids);

  @Query(PLAN + "from SubReadPlanVersion v join SubReadPlan p on p.id=v.planId where v.id=:id")
  Optional<PlanVersionProjection> planVersion(@Param("id") long id);

  @Query(PLAN + SALE + "order by p.id, v.id")
  List<PlanVersionProjection> salePlans(@Param("petType") String petType,
      @Param("today") LocalDate today, Pageable page);

  @Query("select count(p) " + SALE)
  long salePlanCount(@Param("petType") String petType, @Param("today") LocalDate today);

  @Query("""
      select new com.pawcycle.backend.subscription.persistence.projection.SubscriptionReadBatch$Item
      (i.planVersionId, i.skuId, i.quantity) from SubReadPlanItem i
      where i.planVersionId in :ids order by i.planVersionId, i.skuId
      """)
  List<SubscriptionReadBatch.Item> planItems(@Param("ids") List<Long> ids);

  @Query("""
      select new com.pawcycle.backend.subscription.persistence.projection.SubscriptionReadBatch$Cycle
      (c.planVersionId, c.deliveryCycleWeeks) from SubReadCycle c
      where c.planVersionId in :ids order by c.planVersionId, c.deliveryCycleWeeks
      """)
  List<SubscriptionReadBatch.Cycle> cycles(@Param("ids") List<Long> ids);

  @Query(SUBSCRIPTION + "where s.id=:id and s.memberId=:memberId and s.runtimeManaged=true")
  Optional<SubscriptionProjection> ownedSubscription(@Param("memberId") long memberId, @Param("id") long id);

  @Query(SUBSCRIPTION + "where s.memberId=:memberId and s.runtimeManaged=true order by s.id desc")
  List<SubscriptionProjection> subscriptions(@Param("memberId") long memberId, Pageable page);

  @Query("select count(s) from SubReadSubscription s where s.memberId=:memberId and s.runtimeManaged=true")
  long subscriptionCount(@Param("memberId") long memberId);

  @Query(SNAPSHOT + "where s.id in :ids")
  List<SubscriptionSnapshotBase> snapshots(@Param("ids") List<Long> ids);

  @Query("""
      select new com.pawcycle.backend.subscription.persistence.projection.SubscriptionReadBatch$Item
      (i.snapshotId, i.skuId, i.quantity) from SubReadSnapshotItem i
      where i.snapshotId in :ids order by i.snapshotId, i.skuId
      """)
  List<SubscriptionReadBatch.Item> snapshotItems(@Param("ids") List<Long> ids);

  @Query("""
      select new com.pawcycle.backend.subscription.persistence.projection.SubscriptionReadBatch$NextSchedule
      (s.subscriptionId, s.scheduledDate) from SubReadSchedule s
      where s.subscriptionId in :ids and s.status='SCHEDULED' and s.scheduledDate>=:today and
      """ + UNORDERED + " order by s.subscriptionId, s.scheduledDate, s.id")
  List<SubscriptionReadBatch.NextSchedule> nextSchedules(@Param("ids") List<Long> ids,
      @Param("today") LocalDate today);

  @Query("select s.scheduledDate from SubReadSchedule s where s.subscriptionId=:id"
      + " and s.status='SCHEDULED' and s.scheduledDate>=:today and " + UNORDERED
      + " order by s.scheduledDate, s.id")
  List<LocalDate> nextSchedule(@Param("id") long id, @Param("today") LocalDate today, Pageable first);

  @Query("""
      select new com.pawcycle.backend.subscription.persistence.projection.PendingSubscriptionChange
      (p.snapshotId, p.targetScheduleId, s.scheduledDate) from SubReadPendingChange p
      join SubReadSchedule s on s.id=p.targetScheduleId where p.subscriptionId=:id
      """)
  Optional<PendingSubscriptionChange> pendingChange(@Param("id") long id);

  // HELD is eligible even when ordered. Only the SCHEDULED branch excludes existing orders.
  @Query("""
      select new com.pawcycle.backend.subscription.persistence.projection.NextDeliveryProjection
      (s.id, s.scheduledDate, s.status, s.holdReason, s.effectiveSnapshotId) from SubReadSchedule s
      where s.subscriptionId=:id and (s.status='HELD' or (s.status='SCHEDULED' and
      """ + UNORDERED + ")) order by s.scheduledDate, s.id")
  List<NextDeliveryProjection> nextDelivery(@Param("id") long id, Pageable first);

  @Query("""
      select new com.pawcycle.backend.subscription.persistence.projection.SubscriptionItemDetailProjection
      (i.skuId, sku.name, product.id, product.name, product.thumbnailUrl, i.quantity)
      from SubReadSnapshotItem i join Sku sku on sku.id=i.skuId join sku.product product
      where i.snapshotId=:id order by i.skuId
      """)
  List<SubscriptionItemDetailProjection> snapshotItemDetails(@Param("id") long id);

  @Query("""
      select new com.pawcycle.backend.subscription.persistence.projection.ScheduleAddonProjection
      (a.scheduleId, a.skuId, product.id, product.name, sku.name, a.quantity, a.unitPriceKrw)
      from SubReadAddon a join Sku sku on sku.id=a.skuId join sku.product product
      where a.scheduleId=:id order by a.skuId
      """)
  List<ScheduleAddonProjection> addons(@Param("id") long id);

  @Query("select count(a) from SubReadAddon a where a.scheduleId=:id")
  int addonCount(@Param("id") long id);

  @Query("""
      select new com.pawcycle.backend.subscription.persistence.projection.ScheduleViewProjection
      (s.id, s.scheduledDate, s.status, s.effectiveSnapshotId) from SubReadSchedule s
      where s.subscriptionId=:id order by s.scheduledDate desc, s.id desc
      """)
  List<ScheduleViewProjection> schedules(@Param("id") long id, Pageable page);

  @Query("select count(s) from SubReadSchedule s where s.subscriptionId=:id")
  long scheduleCount(@Param("id") long id);

  @Query("""
      select new com.pawcycle.backend.subscription.persistence.projection.SubscriptionReadBatch$History
      (h.commandType, h.occurredAt) from SubReadCommandHistory h
      where h.subscriptionId=:id order by h.occurredAt desc, h.id desc
      """)
  List<SubscriptionReadBatch.History> history(@Param("id") long id, Pageable page);

  @Query("select count(h) from SubReadCommandHistory h where h.subscriptionId=:id")
  long historyCount(@Param("id") long id);
}
