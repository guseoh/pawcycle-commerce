package com.pawcycle.backend.recommendation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import org.springframework.stereotype.Component;

@Component
class RecommendationQueryAdapter {
  private final NativeQueryExecutor jdbc;

  RecommendationQueryAdapter(NativeQueryExecutor jdbc) {
    this.jdbc = jdbc;
  }

  String findOwnedPetType(long memberId, long petId) {
    return jdbc.query(
        "SELECT pet_type FROM pets WHERE id=? AND member_id=?",
        rs -> rs.next() ? rs.getString(1) : null,
        petId,
        memberId);
  }

  List<RecommendationCandidate> findPurchasableCandidates(String petType) {
    String typeClause = petType == null || petType.isBlank() ? "" : " AND product.pet_type=?";
    List<Object> args = petType == null || petType.isBlank() ? List.of() : List.of(petType);
    List<RecommendationCandidate> base =
        jdbc.query(
            """
            SELECT product.id AS product_id,product.name AS product_name,
                   product.short_description AS product_short_description,
                   product.thumbnail_url AS product_thumbnail_url,product.pet_type AS product_pet_type,
                   category.id AS category_id,category.name AS category_name,category.slug AS category_slug,
                   brand.id AS brand_id,brand.name AS brand_name,brand.slug AS brand_slug
            FROM products product JOIN categories category ON category.id=product.category_id
            JOIN brands brand ON brand.id=product.brand_id
            WHERE product.display_status='PUBLIC' AND category.active=true AND brand.active=true
              AND EXISTS (SELECT 1 FROM skus sku JOIN inventories inventory ON inventory.sku_id=sku.id
                         WHERE sku.product_id=product.id AND sku.status='ACTIVE' AND inventory.available_quantity>0)
            """
                + typeClause
                + " ORDER BY product.id",
            (rs, row) ->
                new RecommendationCandidate(
                    rs.getLong(1),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getString(5),
                    new RecommendationCategory(
                        rs.getLong(6), rs.getString(7), rs.getString(8)),
                    new RecommendationBrand(
                        rs.getLong(9), rs.getString(10), rs.getString(11)),
                    List.of(),
                    0),
            args.toArray());
    if (base.isEmpty()) return base;
    Map<Long, List<String>> facets = new HashMap<>();
    jdbc.query(
        "SELECT pfv.product_id,fd.`key`,fo.value FROM product_facet_values pfv JOIN facet_options"
            + " fo ON fo.id=pfv.facet_option_id JOIN facet_definitions fd ON"
            + " fd.id=fo.facet_definition_id WHERE pfv.product_id IN ("
            + placeholders(base.size())
            + ") ORDER BY pfv.product_id,fd.id,fo.display_order,fo.id",
        (NativeQueryExecutor.RowCallbackHandler)
            rs ->
                facets
                    .computeIfAbsent(rs.getLong(1), ignored -> new ArrayList<>())
                    .add(rs.getString(2) + ":" + rs.getString(3)),
        base.stream().map(RecommendationCandidate::productId).toArray());
    Map<Long, Long> popular = popularScores(petType);
    return base.stream()
        .map(
            candidate ->
                new RecommendationCandidate(
                    candidate.productId(),
                    candidate.name(),
                    candidate.shortDescription(),
                    candidate.thumbnailUrl(),
                    candidate.petType(),
                    candidate.category(),
                    candidate.brand(),
                    facets.getOrDefault(candidate.productId(), List.of()),
                    popular.getOrDefault(candidate.productId(), 0L)))
        .toList();
  }

  Set<Long> activeSubscriptionProductIds(long memberId, long petId) {
    return Set.copyOf(
        jdbc.queryForList(
            "SELECT DISTINCT sku.product_id FROM subscriptions subscription JOIN"
                + " subscription_snapshots snapshot ON snapshot.id=subscription.current_snapshot_id"
                + " JOIN subscription_snapshot_items item ON item.snapshot_id=snapshot.id JOIN skus"
                + " sku ON sku.id=item.sku_id WHERE subscription.member_id=? AND"
                + " subscription.pet_id=? AND subscription.runtime_managed=true AND"
                + " subscription.status='ACTIVE'",
            Long.class,
            memberId,
            petId));
  }

  Set<Long> exposedProductIds(long memberId, int days) {
    return Set.copyOf(
        jdbc.queryForList(
            "SELECT DISTINCT product_id FROM interaction_events WHERE member_id=? AND product_id IS"
                + " NOT NULL AND event_type IN"
                + " ('PRODUCT_IMPRESSION','PRODUCT_VIEW','RECOMMENDATION_IMPRESSION') AND"
                + " occurred_at >= DATE_SUB(UTC_TIMESTAMP(6), INTERVAL ? DAY)",
            Long.class,
            memberId,
            days));
  }

  RecommendationMemberSignals memberSignals(long memberId, long petId) {
    Map<Long, Integer> purchases =
        productCounts(
            """
            SELECT sku.product_id,COUNT(*) FROM orders o JOIN payments pay ON pay.order_id=o.id AND pay.status='SUCCEEDED'
            JOIN order_items item ON item.order_id=o.id JOIN skus sku ON sku.id=item.sku_id
            WHERE o.member_id=? AND o.status='PAID' AND o.paid_at >= DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 180 DAY) GROUP BY sku.product_id\
            """,
            memberId);
    Map<Long, Integer> wishlist =
        productCounts(
            "SELECT product_id,COUNT(*) FROM wishlist_items WHERE member_id=? GROUP BY product_id",
            memberId);
    Map<Long, Integer> clicks = interactionCounts(memberId, "RECOMMENDATION_CLICK", 30);
    Map<Long, Integer> views = interactionCounts(memberId, "PRODUCT_VIEW", 30);
    Map<String, Integer> subscriptionCategories =
        categoryCounts(
            """
            SELECT category.slug,COUNT(*) FROM subscriptions subscription JOIN subscription_snapshots snapshot ON snapshot.id=subscription.current_snapshot_id
            JOIN subscription_snapshot_items item ON item.snapshot_id=snapshot.id JOIN skus sku ON sku.id=item.sku_id JOIN products product ON product.id=sku.product_id
            JOIN categories category ON category.id=product.category_id WHERE subscription.member_id=? AND subscription.pet_id=? AND subscription.runtime_managed=true AND subscription.status='ACTIVE' GROUP BY category.slug\
            """,
            memberId,
            petId);
    Map<String, Integer> subscriptionBrands =
        categoryCounts(
            """
            SELECT brand.slug,COUNT(*) FROM subscriptions subscription JOIN subscription_snapshots snapshot ON snapshot.id=subscription.current_snapshot_id
            JOIN subscription_snapshot_items item ON item.snapshot_id=snapshot.id JOIN skus sku ON sku.id=item.sku_id JOIN products product ON product.id=sku.product_id
            JOIN brands brand ON brand.id=product.brand_id WHERE subscription.member_id=? AND subscription.pet_id=? AND subscription.runtime_managed=true AND subscription.status='ACTIVE' GROUP BY brand.slug\
            """,
            memberId,
            petId);
    Map<String, Integer> purchaseCategories =
        categoryCounts(
            "SELECT category.slug,COUNT(*) FROM orders o JOIN payments pay ON pay.order_id=o.id AND"
                + " pay.status='SUCCEEDED' JOIN order_items item ON item.order_id=o.id JOIN skus"
                + " sku ON sku.id=item.sku_id JOIN products product ON product.id=sku.product_id"
                + " JOIN categories category ON category.id=product.category_id WHERE o.member_id=?"
                + " AND o.status='PAID' AND o.paid_at >= DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 180"
                + " DAY) GROUP BY category.slug",
            memberId);
    Map<String, Integer> purchaseBrands =
        categoryCounts(
            "SELECT brand.slug,COUNT(*) FROM orders o JOIN payments pay ON pay.order_id=o.id AND"
                + " pay.status='SUCCEEDED' JOIN order_items item ON item.order_id=o.id JOIN skus"
                + " sku ON sku.id=item.sku_id JOIN products product ON product.id=sku.product_id"
                + " JOIN brands brand ON brand.id=product.brand_id WHERE o.member_id=? AND"
                + " o.status='PAID' AND o.paid_at >= DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 180 DAY)"
                + " GROUP BY brand.slug",
            memberId);
    Map<String, Integer> wishlistCategories =
        categoryCounts(
            "SELECT category.slug,COUNT(*) FROM wishlist_items w JOIN products product ON"
                + " product.id=w.product_id JOIN categories category ON"
                + " category.id=product.category_id WHERE w.member_id=? GROUP BY category.slug",
            memberId);
    Map<String, Integer> wishlistBrands =
        categoryCounts(
            "SELECT brand.slug,COUNT(*) FROM wishlist_items w JOIN products product ON"
                + " product.id=w.product_id JOIN brands brand ON brand.id=product.brand_id WHERE"
                + " w.member_id=? GROUP BY brand.slug",
            memberId);
    Map<String, Integer> filterCategories = filterCounts(memberId, "category");
    Map<String, Integer> filterBrands = filterCounts(memberId, "brand");
    Map<String, Integer> subscriptionFacets =
        facetCounts(
            """
            SELECT CONCAT(fd.`key`,':',fo.value),COUNT(*) FROM subscriptions subscription JOIN subscription_snapshots snapshot ON snapshot.id=subscription.current_snapshot_id JOIN subscription_snapshot_items item ON item.snapshot_id=snapshot.id JOIN skus sku ON sku.id=item.sku_id JOIN product_facet_values pfv ON pfv.product_id=sku.product_id JOIN facet_options fo ON fo.id=pfv.facet_option_id JOIN facet_definitions fd ON fd.id=fo.facet_definition_id WHERE subscription.member_id=? AND subscription.pet_id=? AND subscription.runtime_managed=true AND subscription.status='ACTIVE' GROUP BY fd.`key`,fo.value\
            """,
            memberId,
            petId);
    Map<String, Integer> purchaseFacets =
        facetCounts(
            "SELECT CONCAT(fd.`key`,':',fo.value),COUNT(*) FROM orders o JOIN payments pay ON"
                + " pay.order_id=o.id AND pay.status='SUCCEEDED' JOIN order_items item ON"
                + " item.order_id=o.id JOIN skus sku ON sku.id=item.sku_id JOIN"
                + " product_facet_values pfv ON pfv.product_id=sku.product_id JOIN facet_options fo"
                + " ON fo.id=pfv.facet_option_id JOIN facet_definitions fd ON"
                + " fd.id=fo.facet_definition_id WHERE o.member_id=? AND o.status='PAID' AND"
                + " o.paid_at >= DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 180 DAY) GROUP BY"
                + " fd.`key`,fo.value",
            memberId);
    Map<String, Integer> wishlistFacets =
        facetCounts(
            "SELECT CONCAT(fd.`key`,':',fo.value),COUNT(*) FROM wishlist_items w JOIN"
                + " product_facet_values pfv ON pfv.product_id=w.product_id JOIN facet_options fo"
                + " ON fo.id=pfv.facet_option_id JOIN facet_definitions fd ON"
                + " fd.id=fo.facet_definition_id WHERE w.member_id=? GROUP BY fd.`key`,fo.value",
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
        "SELECT sku.product_id,COUNT(DISTINCT o.member_id)*5 FROM orders o JOIN payments pay ON"
            + " pay.order_id=o.id AND pay.status='SUCCEEDED' JOIN order_items item ON"
            + " item.order_id=o.id JOIN skus sku ON sku.id=item.sku_id WHERE o.status='PAID' AND"
            + " o.paid_at >= DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 30 DAY) GROUP BY sku.product_id");
    addPopular(
        result,
        "SELECT sku.product_id,COUNT(DISTINCT cart.member_id)*3 FROM carts cart JOIN cart_items"
            + " item ON item.cart_id=cart.id JOIN skus sku ON sku.id=item.sku_id WHERE"
            + " cart.updated_at >= DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 30 DAY) GROUP BY"
            + " sku.product_id");
    addPopular(
        result,
        "SELECT product_id,COUNT(DISTINCT member_id)*2 FROM wishlist_items WHERE created_at >="
            + " DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 30 DAY) GROUP BY product_id");
    addPopular(
        result,
        "SELECT product_id,COUNT(DISTINCT member_id)*2 FROM interaction_events WHERE"
            + " event_type='RECOMMENDATION_CLICK' AND occurred_at >= DATE_SUB(UTC_TIMESTAMP(6),"
            + " INTERVAL 30 DAY) GROUP BY product_id");
    addPopular(
        result,
        "SELECT product_id,COUNT(DISTINCT member_id) FROM interaction_events WHERE"
            + " event_type='PRODUCT_VIEW' AND occurred_at >= DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 30"
            + " DAY) GROUP BY product_id");
    if (petType == null || petType.isBlank()) return result;
    Set<Long> matching =
        Set.copyOf(
            jdbc.queryForList("SELECT id FROM products WHERE pet_type=?", Long.class, petType));
    result.keySet().removeIf(id -> !matching.contains(id));
    return result;
  }

  private void addPopular(Map<Long, Long> target, String sql) {
    jdbc.query(
        sql,
        (NativeQueryExecutor.RowCallbackHandler)
            rs -> {
              long id = rs.getLong(1);
              target.merge(id, rs.getLong(2), Long::sum);
            });
  }

  Map<Long, RecommendationTrendScore> trendScores(List<Long> productIds, LocalDate today) {
    if (productIds.isEmpty()) return Map.of();
    String ids = placeholders(productIds.size());
    LocalDate recentStart = today.minusDays(7);
    LocalDate previousStart = today.minusDays(14);
    String sql =
        """
        SELECT activity.product_id,
               COALESCE(SUM(CASE WHEN activity.activity_at>=? AND activity.activity_at<? THEN activity.weight ELSE 0 END),0),
               COALESCE(SUM(CASE WHEN activity.activity_at>=? AND activity.activity_at<? THEN activity.weight ELSE 0 END),0)
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
    jdbc.query(
        sql,
        rs -> {
          while (rs.next())
            result.put(
                rs.getLong(1), new RecommendationTrendScore(rs.getLong(2), rs.getLong(3)));
        },
        arguments.toArray());
    return result;
  }

  List<String> subscriptionCategorySlugs(long memberId, long petId) {
    return categories(
        "SELECT category.slug FROM subscriptions subscription JOIN subscription_snapshots snapshot"
            + " ON snapshot.id=subscription.current_snapshot_id JOIN subscription_snapshot_items"
            + " item ON item.snapshot_id=snapshot.id JOIN skus sku ON sku.id=item.sku_id JOIN"
            + " products product ON product.id=sku.product_id JOIN categories category ON"
            + " category.id=product.category_id WHERE subscription.member_id=? AND"
            + " subscription.pet_id=? AND subscription.runtime_managed=true AND"
            + " subscription.status='ACTIVE' GROUP BY category.id,category.slug ORDER BY COUNT(*)"
            + " DESC,category.id",
        memberId,
        petId);
  }

  List<String> purchaseCategorySlugs(long memberId) {
    return categories(
        "SELECT category.slug FROM orders orders JOIN payments payment ON"
            + " payment.order_id=orders.id AND payment.status='SUCCEEDED' JOIN order_items item ON"
            + " item.order_id=orders.id JOIN skus sku ON sku.id=item.sku_id JOIN products product"
            + " ON product.id=sku.product_id JOIN categories category ON"
            + " category.id=product.category_id WHERE orders.member_id=? AND orders.status='PAID'"
            + " GROUP BY category.id,category.slug ORDER BY COUNT(*) DESC,category.id",
        memberId);
  }

  List<String> wishlistCategorySlugs(long memberId) {
    return categories(
        "SELECT category.slug FROM wishlist_items wishlist JOIN products product ON"
            + " product.id=wishlist.product_id JOIN categories category ON"
            + " category.id=product.category_id WHERE wishlist.member_id=? GROUP BY"
            + " category.id,category.slug ORDER BY COUNT(*) DESC,category.id",
        memberId);
  }

  private Map<Long, Integer> interactionCounts(long memberId, String type, int days) {
    Map<Long, Integer> result = new HashMap<>();
    jdbc.query(
        "SELECT product_id,COUNT(*) FROM interaction_events WHERE member_id=? AND event_type=? AND"
            + " product_id IS NOT NULL AND occurred_at >= DATE_SUB(UTC_TIMESTAMP(6), INTERVAL ?"
            + " DAY) GROUP BY product_id",
        (NativeQueryExecutor.RowCallbackHandler)
            rs -> result.put(rs.getLong(1), rs.getInt(2)),
        memberId,
        type,
        days);
    return result;
  }

  private Map<Long, Integer> productCounts(String sql, Object... args) {
    Map<Long, Integer> result = new HashMap<>();
    jdbc.query(
        sql,
        (NativeQueryExecutor.RowCallbackHandler)
            rs -> result.put(rs.getLong(1), rs.getInt(2)),
        args);
    return result;
  }

  private Map<String, Integer> categoryCounts(String sql, Object... args) {
    Map<String, Integer> result = new HashMap<>();
    jdbc.query(
        sql,
        (NativeQueryExecutor.RowCallbackHandler)
            rs -> result.put(rs.getString(1), rs.getInt(2)),
        args);
    return result;
  }

  private Map<String, Integer> filterCounts(long memberId, String key) {
    Map<String, Integer> result = new HashMap<>();
    jdbc.query(
        "SELECT JSON_UNQUOTE(JSON_EXTRACT(context, '$."
            + key
            + "')),COUNT(*) FROM interaction_events WHERE member_id=? AND event_type='FILTER' AND"
            + " occurred_at >= DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 30 DAY) AND"
            + " JSON_EXTRACT(context, '$."
            + key
            + "') IS NOT NULL GROUP BY JSON_UNQUOTE(JSON_EXTRACT(context, '$."
            + key
            + "'))",
        (NativeQueryExecutor.RowCallbackHandler)
            rs -> result.put(rs.getString(1), rs.getInt(2)),
        memberId);
    return result;
  }

  private Map<String, Integer> facetCounts(String sql, Object... args) {
    Map<String, Integer> result = new HashMap<>();
    jdbc.query(
        sql,
        (NativeQueryExecutor.RowCallbackHandler)
            rs -> result.put(rs.getString(1), rs.getInt(2)),
        args);
    return result;
  }

  private Map<String, Integer> filterFacetCounts(long memberId) {
    Map<String, Integer> result = new HashMap<>();
    jdbc.query(
        "SELECT facets.facet,COUNT(*) FROM interaction_events event JOIN"
            + " JSON_TABLE(event.context,'$.facets[*]' COLUMNS(facet VARCHAR(200) PATH '$')) facets"
            + " WHERE event.member_id=? AND event.event_type='FILTER' AND event.occurred_at >="
            + " DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 30 DAY) GROUP BY facets.facet",
        (NativeQueryExecutor.RowCallbackHandler)
            rs -> result.put(rs.getString(1), rs.getInt(2)),
        memberId);
    return result;
  }

  private List<String> categories(String sql, Object... args) {
    return jdbc.queryForList(sql, String.class, args);
  }

  private String placeholders(int size) {
    return String.join(",", java.util.Collections.nCopies(size, "?"));
  }

}
