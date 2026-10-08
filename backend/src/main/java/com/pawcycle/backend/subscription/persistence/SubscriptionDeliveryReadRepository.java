package com.pawcycle.backend.subscription.persistence;

import com.pawcycle.backend.catalog.product.domain.QProduct;
import com.pawcycle.backend.catalog.sku.domain.QSku;
import com.pawcycle.backend.subscription.persistence.projection.NextDeliveryProjection;
import com.pawcycle.backend.subscription.persistence.projection.PendingSubscriptionChange;
import com.pawcycle.backend.subscription.persistence.projection.ScheduleAddonProjection;
import com.pawcycle.backend.subscription.persistence.projection.ScheduleViewProjection;
import com.pawcycle.backend.subscription.persistence.projection.SubscriptionReadBatch;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/** Typed delivery predicates keep correlated aliases and HELD/SCHEDULED grouping together. */
@Repository
class SubscriptionDeliveryReadRepository {
  private final JPAQueryFactory queries;

  SubscriptionDeliveryReadRepository(EntityManager entities) {
    queries = new JPAQueryFactory(entities);
  }

  List<SubscriptionReadBatch.NextSchedule> nextSchedules(List<Long> ids, LocalDate today) {
    var s = new QSubscriptionReadRows_Schedule("schedule");
    return queries.select(Projections.constructor(SubscriptionReadBatch.NextSchedule.class,
            s.subscriptionId, s.scheduledDate))
        .from(s).where(s.subscriptionId.in(ids), upcoming(s, today))
        .orderBy(s.subscriptionId.asc(), s.scheduledDate.asc(), s.id.asc()).fetch();
  }

  List<LocalDate> nextSchedule(long id, LocalDate today, Pageable first) {
    var s = new QSubscriptionReadRows_Schedule("schedule");
    return queries.select(s.scheduledDate).from(s)
        .where(s.subscriptionId.eq(id), upcoming(s, today))
        .orderBy(s.scheduledDate.asc(), s.id.asc()).limit(first.getPageSize()).fetch();
  }

  Optional<PendingSubscriptionChange> pendingChange(long id) {
    var pending = new QSubscriptionReadRows_PendingChange("pending");
    var s = new QSubscriptionReadRows_Schedule("schedule");
    return Optional.ofNullable(queries.select(Projections.constructor(PendingSubscriptionChange.class,
            pending.snapshotId, pending.targetScheduleId, s.scheduledDate))
        .from(pending).join(s).on(s.id.eq(pending.targetScheduleId))
        .where(pending.subscriptionId.eq(id)).fetchOne());
  }

  List<NextDeliveryProjection> nextDelivery(long id, Pageable first) {
    var s = new QSubscriptionReadRows_Schedule("schedule");
    // HELD remains eligible even if ordered; only SCHEDULED excludes an existing order.
    var eligible = s.status.eq("HELD").or(s.status.eq("SCHEDULED").and(unordered(s)));
    return queries.select(Projections.constructor(NextDeliveryProjection.class,
            s.id, s.scheduledDate, s.status, s.holdReason, s.effectiveSnapshotId))
        .from(s).where(s.subscriptionId.eq(id), eligible)
        .orderBy(s.scheduledDate.asc(), s.id.asc()).limit(first.getPageSize()).fetch();
  }

  List<ScheduleAddonProjection> addons(long id) {
    var addon = new QSubscriptionReadRows_Addon("addon");
    var sku = new QSku("sku");
    var product = new QProduct("product");
    return queries.select(Projections.constructor(ScheduleAddonProjection.class,
            addon.scheduleId, addon.skuId, product.id, product.name, sku.name,
            addon.quantity, addon.unitPriceKrw))
        .from(addon).join(sku).on(sku.id.eq(addon.skuId)).join(sku.product, product)
        .where(addon.scheduleId.eq(id)).orderBy(addon.skuId.asc()).fetch();
  }

  int addonCount(long id) {
    var addon = new QSubscriptionReadRows_Addon("addon");
    return Math.toIntExact(queries.select(addon.count()).from(addon)
        .where(addon.scheduleId.eq(id)).fetchOne());
  }

  List<ScheduleViewProjection> schedules(long id, Pageable page) {
    var s = new QSubscriptionReadRows_Schedule("schedule");
    return queries.select(Projections.constructor(ScheduleViewProjection.class,
            s.id, s.scheduledDate, s.status, s.effectiveSnapshotId))
        .from(s).where(s.subscriptionId.eq(id))
        .orderBy(s.scheduledDate.desc(), s.id.desc())
        .offset(page.getOffset()).limit(page.getPageSize()).fetch();
  }

  long scheduleCount(long id) {
    var s = new QSubscriptionReadRows_Schedule("schedule");
    return queries.select(s.count()).from(s).where(s.subscriptionId.eq(id)).fetchOne();
  }

  private BooleanExpression upcoming(QSubscriptionReadRows_Schedule schedule, LocalDate today) {
    return schedule.status.eq("SCHEDULED").and(schedule.scheduledDate.goe(today)).and(unordered(schedule));
  }

  private BooleanExpression unordered(QSubscriptionReadRows_Schedule schedule) {
    var order = new QSubscriptionReadRows_Order("existingOrder");
    return JPAExpressions.selectOne().from(order).where(order.scheduleId.eq(schedule.id)).notExists();
  }
}
