package com.pawcycle.backend.subscription.persistence;

import com.pawcycle.backend.subscription.persistence.projection.PlanVersionProjection;
import com.pawcycle.backend.subscription.persistence.projection.SubscriptionReadBatch;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Plan/version joins and their batched composition. No command operations. */
interface PlanReadRepository extends Repository<SubscriptionReadRows.PlanVersion, Long> {
  String PLAN = "select p.id, p.name, p.currentPlanVersionId, p.targetPetType, p.onSale,"
      + " p.saleStartsOn, p.saleEndsOn, v.id, v.packagePriceKrw, v.migrationOnly ";
  String SALE = "from SubReadPlan p join SubReadPlanVersion v on v.id=p.currentPlanVersionId"
      + " where p.name is not null and p.targetPetType=:petType and p.onSale=true"
      + " and v.migrationOnly=false and (p.saleStartsOn is null or p.saleStartsOn<=:today)"
      + " and (p.saleEndsOn is null or p.saleEndsOn>=:today) ";
  @Query(PLAN + "from SubReadPlanVersion v join SubReadPlan p on p.id=v.planId where v.id=:id")
  Optional<PlanVersionProjection> planVersion(@Param("id") long id);

  @Query(PLAN + SALE + "order by p.id, v.id")
  List<PlanVersionProjection> salePlans(@Param("petType") String petType,
      @Param("today") LocalDate today, Pageable page);

  @Query("select count(p) " + SALE)
  long salePlanCount(@Param("petType") String petType, @Param("today") LocalDate today);

  @Query("""
      select i.planVersionId, i.skuId, i.quantity from SubReadPlanItem i
      where i.planVersionId in :ids order by i.planVersionId, i.skuId
      """)
  List<SubscriptionReadBatch.Item> planItems(@Param("ids") List<Long> ids);

  @Query("""
      select c.planVersionId, c.deliveryCycleWeeks from SubReadCycle c
      where c.planVersionId in :ids order by c.planVersionId, c.deliveryCycleWeeks
      """)
  List<SubscriptionReadBatch.Cycle> cycles(@Param("ids") List<Long> ids);

}
