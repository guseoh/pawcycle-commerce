package com.pawcycle.backend.subscription.persistence;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.hibernate.type.descriptor.java.LocalDateJavaType;
import org.hibernate.type.descriptor.jdbc.LocalDateJdbcType;
import org.hibernate.type.internal.BasicTypeImpl;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

@Repository
public class RepeatCommerceQueryRepository {
  private final EntityManager entities;
  private final SubscriptionNativeSql nativeSql;

  public RepeatCommerceQueryRepository(EntityManager entities) {
    this.entities = entities;
    this.nativeSql = new SubscriptionNativeSql(entities);
  }

  public List<PurchaseRow> findOneTimePurchaseDates(long memberId) {
    // MySQL DATE(paid_at) must be truncated by the DB and extracted without a UTC Calendar.
    return nativeSql.query(
        "SELECT p.id,p.name,DATE(o.paid_at) purchased_date FROM orders o JOIN payments pay ON"
            + " pay.order_id=o.id AND pay.status='SUCCEEDED' JOIN order_items oi ON"
            + " oi.order_id=o.id JOIN skus sku ON sku.id=oi.sku_id JOIN products p ON"
            + " p.id=sku.product_id WHERE o.member_id=? AND o.source='ONE_TIME' AND o.status='PAID'"
            + " AND o.paid_at IS NOT NULL GROUP BY o.id,p.id,p.name,o.paid_at ORDER BY p.id,"
            + " o.paid_at DESC, o.id DESC", PurchaseRow.class, memberId)
        .addScalar("id", Long.class).addScalar("name", String.class)
        .addScalar("purchased_date", new BasicTypeImpl<>(LocalDateJavaType.INSTANCE, LocalDateJdbcType.INSTANCE))
        .getResultList();
  }

  public Optional<SubscriptionRow> findRuntimeSubscription(long memberId, long subscriptionId) {
    return entities.createQuery("select s.id,s.status,s.deliveryCycleWeeks,coalesce(s.currentSnapshotId,0L)"
        + " from SubReadSubscription s where s.id=:id and s.memberId=:member and s.runtimeManaged=true",
        SubscriptionRow.class).setParameter("id", subscriptionId).setParameter("member", memberId)
        .getResultList().stream().findFirst();
  }

  public List<LocalDate> findSuccessfulScheduleDates(long subscriptionId) {
    return entities.createQuery("select c.scheduledDate from SubscriptionOrderContextEntity c"
        + " join CommerceOrderEntity o on o.id=c.orderId join PaymentEntity p on p.orderId=o.id"
        + " and p.status='SUCCEEDED' where c.subscriptionId=:id and o.source='SUBSCRIPTION'"
        + " and o.status='PAID' and c.scheduledDate is not null order by c.scheduledDate desc,c.orderId desc",
        LocalDate.class).setParameter("id", subscriptionId).getResultList();
  }

  public long findSourcePlanVersionId(long snapshotId) {
    List<Long> values = entities.createQuery("select s.planVersionId from SubReadSnapshot s where s.id=:id",
        Long.class).setParameter("id", snapshotId).getResultList();
    // Preserve queryForObject's no-row exception, including the null-unboxing contract.
    if (values.isEmpty()) throw new EmptyResultDataAccessException(1);
    return values.getFirst();
  }

  public List<Integer> findAllowedDeliveryCycles(long planVersionId) {
    return entities.createQuery("select c.deliveryCycleWeeks from SubReadCycle c where c.planVersionId=:id"
        + " order by c.deliveryCycleWeeks", Integer.class).setParameter("id", planVersionId).getResultList();
  }

  public Optional<Long> findPaidOneTimeOrder(long memberId, long orderId) {
    return entities.createQuery("select o.id from CommerceOrderEntity o where o.id=:id and o.memberId=:member"
        + " and o.source='ONE_TIME' and o.status='PAID' and exists (select 1 from PaymentEntity p"
        + " where p.orderId=o.id and p.status='SUCCEEDED')", Long.class)
        .setParameter("id", orderId).setParameter("member", memberId).getResultList().stream().findFirst();
  }

  public Set<Long> findOrderProductIds(long orderId) {
    return new LinkedHashSet<>(entities.createQuery("select distinct sku.product.id from CommerceOrderItemEntity i"
        + " join Sku sku on sku.id=i.skuId where i.orderId=:id", Long.class)
        .setParameter("id", orderId).getResultList());
  }

  public List<PlanOptionRow> findAvailablePlanOptions(LocalDate today, long memberId) {
    List<PlanVersionRow> versions = entities.unwrap(org.hibernate.Session.class).createQuery("select v.id,p.name,p.targetPetType,v.packagePriceKrw"
        + " from SubReadPlanVersion v join SubReadPlan p on p.id=v.planId where p.currentPlanVersionId=v.id"
        + " and p.name is not null and p.onSale=true and v.migrationOnly=false"
        + " and (p.saleStartsOn is null or p.saleStartsOn<=:today)"
        + " and (p.saleEndsOn is null or p.saleEndsOn>=:today) order by v.id", PlanVersionRow.class)
        .setParameter("today", today, new BasicTypeImpl<>(LocalDateJavaType.INSTANCE, LocalDateJdbcType.INSTANCE))
        .getResultList();
    List<PlanOptionRow> options = new ArrayList<>();
    // Keep the original 1 + 3N round trips. No entity hydration or lazy association reads.
    for (PlanVersionRow version : versions) {
      List<Long> products = entities.createQuery("select distinct sku.product.id from SubReadPlanItem i"
          + " join Sku sku on sku.id=i.skuId where i.planVersionId=:id", Long.class)
          .setParameter("id", version.planVersionId()).getResultList();
      List<Long> pets = entities.createQuery("select p.id from SubReadPet p where p.memberId=:member"
          + " and p.petType=:type order by p.id", Long.class)
          .setParameter("member", memberId).setParameter("type", version.targetPetType()).getResultList();
      options.add(new PlanOptionRow(version.planVersionId(), version.planName(),
          BigDecimal.valueOf(version.packagePriceKrw()), products, pets,
          findAllowedDeliveryCycles(version.planVersionId())));
    }
    return options;
  }

  public record PurchaseRow(long productId, String productName, LocalDate purchasedDate) {}
  public record SubscriptionRow(long id, String status, int deliveryCycleWeeks, long currentSnapshotId) {}
  public record PlanOptionRow(long planVersionId, String planName, BigDecimal packagePriceKrw,
      List<Long> productIds, List<Long> petIds, List<Integer> allowedDeliveryCycleWeeks) {}
  public record PlanVersionRow(long planVersionId, String planName, String targetPetType, long packagePriceKrw) {}
}
