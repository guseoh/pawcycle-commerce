package com.pawcycle.backend.subscription.persistence;

import com.pawcycle.backend.subscription.persistence.projection.SubscriptionItemDetailProjection;
import com.pawcycle.backend.subscription.persistence.projection.SubscriptionProjection;
import com.pawcycle.backend.subscription.persistence.projection.SubscriptionReadBatch;
import com.pawcycle.backend.subscription.persistence.projection.SubscriptionSnapshotBase;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Subscription identity, immutable snapshots, and command history reads. */
interface SubscriptionReadRepository extends Repository<SubscriptionReadRows.Subscription, Long> {
  Optional<SubscriptionProjection> findByMemberIdAndIdAndRuntimeManagedTrue(long memberId, long id);

  List<SubscriptionProjection> findByMemberIdAndRuntimeManagedTrueOrderByIdDesc(long memberId, Pageable page);

  long countByMemberIdAndRuntimeManagedTrue(long memberId);

  String SNAPSHOT = "select s.id, s.planVersionId, s.packagePriceKrw, s.deliveryCycleWeeks from SubReadSnapshot s ";

  @Query(SNAPSHOT + "where s.id in :ids")
  List<SubscriptionSnapshotBase> snapshots(@Param("ids") List<Long> ids);

  @Query("""
      select i.snapshotId, i.skuId, i.quantity from SubReadSnapshotItem i
      where i.snapshotId in :ids order by i.snapshotId, i.skuId
      """)
  List<SubscriptionReadBatch.Item> snapshotItems(@Param("ids") List<Long> ids);

  @Query("""
      select i.skuId, sku.name, product.id, product.name, product.thumbnailUrl, i.quantity
      from SubReadSnapshotItem i join Sku sku on sku.id=i.skuId join sku.product product
      where i.snapshotId=:id order by i.skuId
      """)
  List<SubscriptionItemDetailProjection> snapshotItemDetails(@Param("id") long id);

  @Query("""
      select h.commandType, h.occurredAt from SubReadCommandHistory h
      where h.subscriptionId=:id order by h.occurredAt desc, h.id desc
      """)
  List<SubscriptionReadBatch.History> history(@Param("id") long id, Pageable page);

  @Query("select count(h) from SubReadCommandHistory h where h.subscriptionId=:id")
  long historyCount(@Param("id") long id);
}
