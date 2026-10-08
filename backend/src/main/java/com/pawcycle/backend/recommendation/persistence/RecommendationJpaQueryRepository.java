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
import org.springframework.stereotype.Repository;

@Repository
class RecommendationJpaQueryRepository {
  private final JPAQueryFactory queries;

  RecommendationJpaQueryRepository(EntityManager entities) {
    this.queries = new JPAQueryFactory(entities);
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
}
