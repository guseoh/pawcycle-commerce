package com.pawcycle.backend.recommendation.persistence;

import com.pawcycle.backend.catalog.admin.domain.QFacetDefinitionEntity;
import com.pawcycle.backend.catalog.admin.domain.QFacetOptionEntity;
import com.pawcycle.backend.catalog.admin.domain.QProductFacetValueEntity;
import com.pawcycle.backend.catalog.brand.domain.QBrand;
import com.pawcycle.backend.catalog.category.domain.QCategory;
import com.pawcycle.backend.catalog.product.domain.ProductStatus;
import com.pawcycle.backend.catalog.product.domain.QProduct;
import com.pawcycle.backend.catalog.sku.domain.QSku;
import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.commerce.inventory.persistence.QInventoryEntity;
import com.pawcycle.backend.commerce.order.domain.QCommerceOrderEntity;
import com.pawcycle.backend.commerce.order.domain.QCommerceOrderItemEntity;
import com.pawcycle.backend.commerce.payment.domain.QPaymentEntity;
import com.pawcycle.backend.commerce.wishlist.domain.QWishlistItemEntity;
import com.pawcycle.backend.recommendation.domain.RecommendationBrand;
import com.pawcycle.backend.recommendation.domain.RecommendationCandidate;
import com.pawcycle.backend.recommendation.domain.RecommendationCategory;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDate;
import com.pawcycle.backend.recommendation.domain.RecommendationMemberSignals;
import com.pawcycle.backend.recommendation.domain.RecommendationTrendScore;
import org.springframework.stereotype.Repository;

@Repository
class RecommendationJpaQueryRepository {
  private final JPAQueryFactory queries;
  private final EntityManager entities;

  RecommendationJpaQueryRepository(EntityManager entities) {
    this.queries = new JPAQueryFactory(entities);
    this.entities = entities;
  }

  Map<Long, Long> coPurchaseCounts(long productId) {
    var order = new QCommerceOrderEntity("order");
    var payment = new QPaymentEntity("payment");
    var sourceItem = new QCommerceOrderItemEntity("sourceItem");
    var sourceSku = new QSku("sourceSku");
    var otherItem = new QCommerceOrderItemEntity("otherItem");
    var otherSku = new QSku("otherSku");
    var otherProduct = new QProduct("otherProduct");
    List<ProductCount> counts = queries.select(Projections.constructor(ProductCount.class,
            otherProduct.id, order.id.countDistinct()))
        .from(order).join(payment).on(payment.orderId.eq(order.id), payment.status.eq("SUCCEEDED"))
        .join(sourceItem).on(sourceItem.orderId.eq(order.id)).join(sourceSku).on(sourceSku.id.eq(sourceItem.skuId))
        .join(otherItem).on(otherItem.orderId.eq(order.id), otherItem.skuId.ne(sourceItem.skuId))
        .join(otherSku).on(otherSku.id.eq(otherItem.skuId)).join(otherProduct).on(otherProduct.id.eq(otherSku.product.id))
        .where(order.source.eq("ONE_TIME"), order.status.eq("PAID"), sourceSku.product.id.eq(productId), otherProduct.id.ne(productId))
        .groupBy(otherProduct.id).fetch();
    Map<Long, Long> result = new HashMap<>();
    for (ProductCount count : counts) result.put(count.productId(), count.count());
    return result;
  }

  List<RecommendationCandidate> findPurchasableCandidates(String petType) {
    var product = new QProduct("product");
    var category = new QCategory("category");
    var brand = new QBrand("brand");
    var sku = new QSku("sku");
    var inventory = new QInventoryEntity("inventory");
    var eligible = new BooleanBuilder(product.status.eq(ProductStatus.PUBLIC))
        .and(category.active.isTrue()).and(brand.active.isTrue())
        .and(JPAExpressions.selectOne().from(sku).join(inventory).on(inventory.skuId.eq(sku.id))
            .where(sku.product.id.eq(product.id), sku.status.eq(SkuStatus.ACTIVE), inventory.availableQuantity.gt(0)).exists());
    if (petType != null && !petType.isBlank()) eligible.and(product.petType.eq(petType));
    List<CandidateRow> rows = queries.select(Projections.constructor(CandidateRow.class,
            product.id, product.name, product.shortDescription, product.thumbnailUrl, product.petType,
            category.id, category.name, category.slug, brand.id, brand.name, brand.slug))
        .from(product).join(product.category, category).join(brand).on(brand.id.eq(product.brandId))
        .where(eligible).orderBy(product.id.asc()).fetch();
    if (rows.isEmpty()) return List.of();
    var value = new QProductFacetValueEntity("value");
    var option = new QFacetOptionEntity("option");
    var definition = new QFacetDefinitionEntity("definition");
    List<FacetRow> facetRows = queries.select(Projections.constructor(FacetRow.class,
            value.product.id, definition.key, option.value))
        .from(value).join(value.facetOption, option).join(option.facetDefinition, definition)
        .where(value.product.id.in(rows.stream().map(CandidateRow::productId).toList()))
        .orderBy(value.product.id.asc(), definition.id.asc(), option.displayOrder.asc(), option.id.asc()).fetch();
    Map<Long, List<String>> facets = new HashMap<>();
    for (FacetRow facet : facetRows) facets.computeIfAbsent(facet.productId(), ignored -> new ArrayList<>())
        .add(facet.key() + ":" + facet.value());
    return rows.stream().map(row -> row.toCandidate(facets.getOrDefault(row.productId(), List.of()))).toList();
  }

  List<String> purchaseCategorySlugs(long memberId) {
    var order = new QCommerceOrderEntity("order");
    var payment = new QPaymentEntity("payment");
    var item = new QCommerceOrderItemEntity("item");
    var sku = new QSku("sku");
    var product = new QProduct("product");
    var category = new QCategory("category");
    return queries.select(category.slug).from(order)
        .join(payment).on(payment.orderId.eq(order.id), payment.status.eq("SUCCEEDED"))
        .join(item).on(item.orderId.eq(order.id)).join(sku).on(sku.id.eq(item.skuId))
        .join(product).on(product.id.eq(sku.product.id)).join(category).on(category.id.eq(product.category.id))
        .where(order.memberId.eq(memberId), order.status.eq("PAID"))
        .groupBy(category.id, category.slug).orderBy(order.id.count().desc(), category.id.asc()).fetch();
  }

  List<String> wishlistCategorySlugs(long memberId) {
    var wishlist = new QWishlistItemEntity("wishlist");
    var product = new QProduct("product");
    var category = new QCategory("category");
    return queries.select(category.slug).from(wishlist)
        .join(product).on(product.id.eq(wishlist.id.productId)).join(category).on(category.id.eq(product.category.id))
        .where(wishlist.id.memberId.eq(memberId)).groupBy(category.id, category.slug)
        .orderBy(product.id.count().desc(), category.id.asc()).fetch();
  }

  public record ProductCount(long productId, long count) {}
  public record FacetRow(long productId, String key, String value) {}
  public record CandidateRow(long productId, String name, String shortDescription, String thumbnailUrl,
      String petType, long categoryId, String categoryName, String categorySlug,
      long brandId, String brandName, String brandSlug) {
    RecommendationCandidate toCandidate(List<String> facets) {
      return new RecommendationCandidate(productId, name, shortDescription, thumbnailUrl, petType,
          new RecommendationCategory(categoryId, categoryName, categorySlug),
          new RecommendationBrand(brandId, brandName, brandSlug), facets, 0);
    }
  }
  String findOwnedPetType(long memberId, long petId) {
    return read("""
        SELECT r.petType
        FROM SubReadPet r
        WHERE r.id=?1
          AND r.memberId=?2
        """, String.class, petId, memberId).stream().findFirst().orElse(null);
  }

  Set<Long> activeSubscriptionProductIds(long memberId, long petId) {
    return Set.copyOf(
        read(
            """
        SELECT DISTINCT sku.product.id
        FROM SubReadSubscription subscription
        JOIN SubReadSnapshot snapshot ON snapshot.id=subscription.currentSnapshotId
        JOIN SubReadSnapshotItem item ON item.snapshotId=snapshot.id
        JOIN Sku sku ON sku.id=item.skuId
        WHERE subscription.memberId=?1
          AND subscription.petId=?2
          AND subscription.runtimeManaged=true
          AND subscription.status='ACTIVE'
        """,
            Long.class,
            memberId,
            petId));
  }

  Set<Long> exposedProductIds(long memberId, int days) {
    return Set.copyOf(
        read(
            """
        SELECT DISTINCT r.productId
        FROM RecommendationEventRead r
        WHERE r.memberId=?1
          AND r.productId IS NOT NULL
          AND r.eventType IN ('PRODUCT_IMPRESSION','PRODUCT_VIEW','RECOMMENDATION_IMPRESSION')
          AND r.occurredAt >= cast(sql('DATE_SUB(UTC_TIMESTAMP(6), INTERVAL ? DAY)', ?2) as LocalDateTime)
        """,
            Long.class,
            memberId,
            days));
  }

  RecommendationMemberSignals memberSignals(long memberId, long petId) {
    Map<Long, Integer> purchases =
        productCounts(
            """
        SELECT sku.product.id,COUNT(*)
        FROM CommerceOrderEntity o
        JOIN PaymentEntity pay ON pay.orderId=o.id
          AND pay.status='SUCCEEDED'
        JOIN CommerceOrderItemEntity item ON item.orderId=o.id
        JOIN Sku sku ON sku.id=item.skuId
        WHERE o.memberId=?1
          AND o.status='PAID'
          AND o.paidAt >= cast(sql('DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 180 DAY)') as LocalDateTime)
        GROUP BY sku.product.id
        """,
            memberId);
    Map<Long, Integer> wishlist =
        productCounts(
            """
        SELECT r.id.productId,COUNT(*)
        FROM WishlistItemEntity r
        WHERE r.id.memberId=?1
        GROUP BY r.id.productId
        """,
            memberId);
    Map<Long, Integer> clicks = interactionCounts(memberId, "RECOMMENDATION_CLICK", 30);
    Map<Long, Integer> views = interactionCounts(memberId, "PRODUCT_VIEW", 30);
    Map<String, Integer> subscriptionCategories =
        categoryCounts(
            """
        SELECT category.slug,COUNT(*)
        FROM SubReadSubscription subscription
        JOIN SubReadSnapshot snapshot ON snapshot.id=subscription.currentSnapshotId
        JOIN SubReadSnapshotItem item ON item.snapshotId=snapshot.id
        JOIN Sku sku ON sku.id=item.skuId
        JOIN Product product ON product.id=sku.product.id
        JOIN Category category ON category.id=product.category.id
        WHERE subscription.memberId=?1
          AND subscription.petId=?2
          AND subscription.runtimeManaged=true
          AND subscription.status='ACTIVE'
        GROUP BY category.slug
        """,
            memberId,
            petId);
    Map<String, Integer> subscriptionBrands =
        categoryCounts(
            """
        SELECT brand.slug,COUNT(*)
        FROM SubReadSubscription subscription
        JOIN SubReadSnapshot snapshot ON snapshot.id=subscription.currentSnapshotId
        JOIN SubReadSnapshotItem item ON item.snapshotId=snapshot.id
        JOIN Sku sku ON sku.id=item.skuId
        JOIN Product product ON product.id=sku.product.id
        JOIN Brand brand ON brand.id=product.brandId
        WHERE subscription.memberId=?1
          AND subscription.petId=?2
          AND subscription.runtimeManaged=true
          AND subscription.status='ACTIVE'
        GROUP BY brand.slug
        """,
            memberId,
            petId);
    Map<String, Integer> purchaseCategories =
        categoryCounts(
            """
        SELECT category.slug,COUNT(*)
        FROM CommerceOrderEntity o
        JOIN PaymentEntity pay ON pay.orderId=o.id
          AND pay.status='SUCCEEDED'
        JOIN CommerceOrderItemEntity item ON item.orderId=o.id
        JOIN Sku sku ON sku.id=item.skuId
        JOIN Product product ON product.id=sku.product.id
        JOIN Category category ON category.id=product.category.id
        WHERE o.memberId=?1
          AND o.status='PAID'
          AND o.paidAt >= cast(sql('DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 180 DAY)') as LocalDateTime)
        GROUP BY category.slug
        """,
            memberId);
    Map<String, Integer> purchaseBrands =
        categoryCounts(
            """
        SELECT brand.slug,COUNT(*)
        FROM CommerceOrderEntity o
        JOIN PaymentEntity pay ON pay.orderId=o.id
          AND pay.status='SUCCEEDED'
        JOIN CommerceOrderItemEntity item ON item.orderId=o.id
        JOIN Sku sku ON sku.id=item.skuId
        JOIN Product product ON product.id=sku.product.id
        JOIN Brand brand ON brand.id=product.brandId
        WHERE o.memberId=?1
          AND o.status='PAID'
          AND o.paidAt >= cast(sql('DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 180 DAY)') as LocalDateTime)
        GROUP BY brand.slug
        """,
            memberId);
    Map<String, Integer> wishlistCategories =
        categoryCounts(
            """
        SELECT category.slug,COUNT(*)
        FROM WishlistItemEntity w
        JOIN Product product ON product.id=w.id.productId
        JOIN Category category ON category.id=product.category.id
        WHERE w.id.memberId=?1
        GROUP BY category.slug
        """,
            memberId);
    Map<String, Integer> wishlistBrands =
        categoryCounts(
            """
        SELECT brand.slug,COUNT(*)
        FROM WishlistItemEntity w
        JOIN Product product ON product.id=w.id.productId
        JOIN Brand brand ON brand.id=product.brandId
        WHERE w.id.memberId=?1
        GROUP BY brand.slug
        """,
            memberId);
    Map<String, Integer> filterCategories = filterCounts(memberId, "category");
    Map<String, Integer> filterBrands = filterCounts(memberId, "brand");
    Map<String, Integer> subscriptionFacets =
        facetCounts(
            """
        SELECT CONCAT(fd.key,':',fo.value),COUNT(*)
        FROM SubReadSubscription subscription
        JOIN SubReadSnapshot snapshot ON snapshot.id=subscription.currentSnapshotId
        JOIN SubReadSnapshotItem item ON item.snapshotId=snapshot.id
        JOIN Sku sku ON sku.id=item.skuId
        JOIN ProductFacetValueEntity pfv ON pfv.product.id=sku.product.id
        JOIN FacetOptionEntity fo ON fo.id=pfv.facetOption.id
        JOIN FacetDefinitionEntity fd ON fd.id=fo.facetDefinition.id
        WHERE subscription.memberId=?1
          AND subscription.petId=?2
          AND subscription.runtimeManaged=true
          AND subscription.status='ACTIVE'
        GROUP BY fd.key,fo.value
        """,
            memberId,
            petId);
    Map<String, Integer> purchaseFacets =
        facetCounts(
            """
        SELECT CONCAT(fd.key,':',fo.value),COUNT(*)
        FROM CommerceOrderEntity o
        JOIN PaymentEntity pay ON pay.orderId=o.id
          AND pay.status='SUCCEEDED'
        JOIN CommerceOrderItemEntity item ON item.orderId=o.id
        JOIN Sku sku ON sku.id=item.skuId
        JOIN ProductFacetValueEntity pfv ON pfv.product.id=sku.product.id
        JOIN FacetOptionEntity fo ON fo.id=pfv.facetOption.id
        JOIN FacetDefinitionEntity fd ON fd.id=fo.facetDefinition.id
        WHERE o.memberId=?1
          AND o.status='PAID'
          AND o.paidAt >= cast(sql('DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 180 DAY)') as LocalDateTime)
        GROUP BY fd.key,fo.value
        """,
            memberId);
    Map<String, Integer> wishlistFacets =
        facetCounts(
            """
        SELECT CONCAT(fd.key,':',fo.value),COUNT(*)
        FROM WishlistItemEntity w
        JOIN ProductFacetValueEntity pfv ON pfv.product.id=w.id.productId
        JOIN FacetOptionEntity fo ON fo.id=pfv.facetOption.id
        JOIN FacetDefinitionEntity fd ON fd.id=fo.facetDefinition.id
        WHERE w.id.memberId=?1
        GROUP BY fd.key,fo.value
        """,
            memberId);
    Map<String, Integer> filterFacets = filterFacetCounts(memberId);
    return new RecommendationMemberSignals(
        purchases,
        wishlist,
        clicks,
        views,
        subscriptionCategories,
        subscriptionBrands,
        subscriptionFacets,
        purchaseCategories,
        purchaseBrands,
        purchaseFacets,
        wishlistCategories,
        wishlistBrands,
        wishlistFacets,
        filterCategories,
        filterBrands,
        filterFacets);
  }

  Map<Long, Long> popularScores(String petType) {
    Map<Long, Long> result = new HashMap<>();
    addPopular(
        result,
        """
        SELECT sku.product.id,COUNT(DISTINCT o.memberId)*5
        FROM CommerceOrderEntity o
        JOIN PaymentEntity pay ON pay.orderId=o.id
          AND pay.status='SUCCEEDED'
        JOIN CommerceOrderItemEntity item ON item.orderId=o.id
        JOIN Sku sku ON sku.id=item.skuId
        WHERE o.status='PAID'
          AND o.paidAt >= cast(sql('DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 30 DAY)') as LocalDateTime)
        GROUP BY sku.product.id
        """);
    addPopular(
        result,
        """
        SELECT sku.product.id,COUNT(DISTINCT cart.memberId)*3
        FROM CartEntity cart
        JOIN CartItemEntity item ON item.id.cartId=cart.id
        JOIN Sku sku ON sku.id=item.id.skuId
        WHERE cart.updatedAt >= cast(sql('DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 30 DAY)') as LocalDateTime)
        GROUP BY sku.product.id
        """);
    addPopular(
        result,
        """
        SELECT r.id.productId,COUNT(DISTINCT r.id.memberId)*2
        FROM WishlistItemEntity r
        WHERE r.createdAt >= cast(sql('DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 30 DAY)') as LocalDateTime)
        GROUP BY r.id.productId
        """);
    addPopular(
        result,
        """
        SELECT coalesce(r.productId,0L),COUNT(DISTINCT r.memberId)*2
        FROM RecommendationEventRead r
        WHERE r.eventType='RECOMMENDATION_CLICK'
          AND r.occurredAt >= cast(sql('DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 30 DAY)') as LocalDateTime)
        GROUP BY r.productId
        """);
    addPopular(
        result,
        """
        SELECT coalesce(r.productId,0L),COUNT(DISTINCT r.memberId)
        FROM RecommendationEventRead r
        WHERE r.eventType='PRODUCT_VIEW'
          AND r.occurredAt >= cast(sql('DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 30 DAY)') as LocalDateTime)
        GROUP BY r.productId
        """);
    if (petType == null || petType.isBlank()) return result;
    Set<Long> matching =
        Set.copyOf(
            read("""
        SELECT r.id
        FROM Product r
        WHERE r.petType=?1
        """, Long.class, petType));
    result.keySet().removeIf(id -> !matching.contains(id));
    return result;
  }

  private void addPopular(Map<Long, Long> target, String hql) {
    for (ProductCount row : read(hql, ProductCount.class)) {
      // JDBC getLong returned zero for a NULL product_id popularity group.
      target.merge(row.productId(), row.count(), Long::sum);
    }
  }

  Map<Long, RecommendationTrendScore> trendScores(List<Long> productIds, LocalDate today) {
    if (productIds.isEmpty()) return Map.of();
    String ids = placeholders(productIds.size());
    LocalDate recentStart = today.minusDays(7);
    LocalDate previousStart = today.minusDays(14);
    String sql =
        """
        SELECT activity.product_id AS productId,
               COALESCE(SUM(CASE WHEN activity.activity_at>=? AND activity.activity_at<? THEN activity.weight ELSE 0 END),0) AS recentScore,
               COALESCE(SUM(CASE WHEN activity.activity_at>=? AND activity.activity_at<? THEN activity.weight ELSE 0 END),0) AS previousScore
        FROM (
         SELECT sku.product_id,o.paid_at activity_at,5 weight FROM orders o
         JOIN payments p ON p.order_id=o.id AND p.status='SUCCEEDED'
         JOIN order_items oi ON oi.order_id=o.id JOIN skus sku ON sku.id=oi.sku_id
         WHERE o.status='PAID'
         UNION ALL SELECT w.product_id,w.created_at,2 FROM wishlist_items w
         UNION ALL SELECT e.product_id,e.occurred_at,2 FROM interaction_events e WHERE e.event_type='RECOMMENDATION_CLICK'
         UNION ALL SELECT e.product_id,e.occurred_at,1 FROM interaction_events e WHERE e.event_type='PRODUCT_VIEW'
        ) activity
        WHERE activity.product_id IN (\
        """
            + ids
            + ") GROUP BY activity.product_id";
    Map<Long, RecommendationTrendScore> result = new HashMap<>();
    List<Object> arguments =
        new ArrayList<>(List.of(recentStart, today, previousStart, recentStart));
    arguments.addAll(productIds);
    for (TrendRow row : nativeQuery(sql, TrendRow.class, arguments.toArray())
        .addScalar("productId", Long.class).addScalar("recentScore", Long.class)
        .addScalar("previousScore", Long.class).getResultList()) {
      result.put(row.productId(), new RecommendationTrendScore(row.recentScore(), row.previousScore()));
    }
    return result;
  }

  List<String> subscriptionCategorySlugs(long memberId, long petId) {
    return categories(
        """
        SELECT category.slug
        FROM SubReadSubscription subscription
        JOIN SubReadSnapshot snapshot ON snapshot.id=subscription.currentSnapshotId
        JOIN SubReadSnapshotItem item ON item.snapshotId=snapshot.id
        JOIN Sku sku ON sku.id=item.skuId
        JOIN Product product ON product.id=sku.product.id
        JOIN Category category ON category.id=product.category.id
        WHERE subscription.memberId=?1
          AND subscription.petId=?2
          AND subscription.runtimeManaged=true
          AND subscription.status='ACTIVE'
        GROUP BY category.id,category.slug
        ORDER BY COUNT(*) DESC,category.id
        """,
        memberId,
        petId);
  }

  private Map<Long, Integer> interactionCounts(long memberId, String type, int days) {
    return productCounts("""
        SELECT r.productId,COUNT(*)
        FROM RecommendationEventRead r
        WHERE r.memberId=?1
          AND r.eventType=?2
          AND r.productId IS NOT NULL
          AND r.occurredAt >= cast(sql('DATE_SUB(UTC_TIMESTAMP(6), INTERVAL ? DAY)', ?3) as LocalDateTime)
        GROUP BY r.productId
        """, memberId, type, days);
  }

  private Map<Long, Integer> productCounts(String hql, Object... args) {
    Map<Long, Integer> result = new HashMap<>();
    for (ProductCount row : read(hql, ProductCount.class, args)) {
      result.put(row.productId(), Math.toIntExact(row.count()));
    }
    return result;
  }

  private Map<String, Integer> categoryCounts(String hql, Object... args) {
    Map<String, Integer> result = new HashMap<>();
    for (TextCount row : read(hql, TextCount.class, args)) {
      result.put(row.signal(), Math.toIntExact(row.count()));
    }
    return result;
  }

  private Map<String, Integer> filterCounts(long memberId, String key) {
    Map<String, Integer> result = new HashMap<>();
    for (TextCount row : nativeQuery(
        "SELECT JSON_UNQUOTE(JSON_EXTRACT(context, '$."
            + key
            + "')) AS signalKey,COUNT(*) AS signalCount FROM interaction_events WHERE member_id=? AND event_type='FILTER' AND"
            + " occurred_at >= DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 30 DAY) AND"
            + " JSON_EXTRACT(context, '$."
            + key
            + "') IS NOT NULL GROUP BY JSON_UNQUOTE(JSON_EXTRACT(context, '$."
            + key
            + "'))",
        TextCount.class, memberId).addScalar("signalKey", String.class)
        .addScalar("signalCount", Long.class).getResultList()) {
      result.put(row.signal(), Math.toIntExact(row.count()));
    }
    return result;
  }

  private Map<String, Integer> facetCounts(String hql, Object... args) {
    Map<String, Integer> result = new HashMap<>();
    for (TextCount row : read(hql, TextCount.class, args)) {
      result.put(row.signal(), Math.toIntExact(row.count()));
    }
    return result;
  }

  private Map<String, Integer> filterFacetCounts(long memberId) {
    Map<String, Integer> result = new HashMap<>();
    for (TextCount row : nativeQuery(
        "SELECT facets.facet AS signalKey,COUNT(*) AS signalCount FROM interaction_events event JOIN"
            + " JSON_TABLE(event.context,'$.facets[*]' COLUMNS(facet VARCHAR(200) PATH '$')) facets"
            + " WHERE event.member_id=? AND event.event_type='FILTER' AND event.occurred_at >="
            + " DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 30 DAY) GROUP BY facets.facet",
        TextCount.class, memberId).addScalar("signalKey", String.class)
        .addScalar("signalCount", Long.class).getResultList()) {
      result.put(row.signal(), Math.toIntExact(row.count()));
    }
    return result;
  }

  private List<String> categories(String sql, Object... args) {
    return read(sql, String.class, args);
  }

  private String placeholders(int size) {
    return String.join(",", java.util.Collections.nCopies(size, "?"));
  }

  private <T> List<T> read(String hql, Class<T> type, Object... args) {
    var query = entities.createQuery(hql, type);
    for (int i = 0; i < args.length; i++) query.setParameter(i + 1, args[i]);
    return query.getResultList();
  }

  // JSON_TABLE and UNION ALL retain their MySQL query shape and DB-clock semantics.
  private <T> org.hibernate.query.NativeQuery<T> nativeQuery(String sql, Class<T> type, Object... args) {
    var query = entities.unwrap(org.hibernate.Session.class).createNativeQuery(sql, type);
    for (int i = 0; i < args.length; i++) {
      if (args[i] instanceof LocalDate date) {
        query.setParameter(i + 1, date, new org.hibernate.type.internal.BasicTypeImpl<>(
            org.hibernate.type.descriptor.java.LocalDateJavaType.INSTANCE,
            org.hibernate.type.descriptor.jdbc.LocalDateJdbcType.INSTANCE));
      } else query.setParameter(i + 1, args[i]);
    }
    return query;
  }

  public record TextCount(String signal, long count) {}
  public record TrendRow(long productId, long recentScore, long previousScore) {}
}
