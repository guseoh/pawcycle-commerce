package com.pawcycle.backend.catalog.product.persistence;

import com.pawcycle.backend.catalog.product.application.BrandSummary;
import com.pawcycle.backend.catalog.product.application.CategorySummary;
import com.pawcycle.backend.catalog.product.application.ProductDetailSkuRow;
import com.pawcycle.backend.catalog.product.application.ProductDetailSupplement;
import com.pawcycle.backend.catalog.product.application.ProductImage;
import com.pawcycle.backend.catalog.product.application.ProductListView;
import com.pawcycle.backend.catalog.product.application.ProductOptionGroup;
import com.pawcycle.backend.catalog.product.application.ProductOptionValue;
import com.pawcycle.backend.catalog.product.application.ProductSelectedOption;
import com.pawcycle.backend.catalog.product.application.ProductSort;
import com.pawcycle.backend.catalog.product.application.ProductSummary;
import com.pawcycle.backend.catalog.product.application.SkuPrice;
import com.pawcycle.backend.catalog.product.application.SkuPriceSummary;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Authoritative discovery read model; dynamic filtering is kept in one JPA query boundary. */
@Repository
public class ProductDiscoveryQueryRepository {
  private final EntityManager entityManager;

  public ProductDiscoveryQueryRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Transactional(readOnly = true)
  public ProductListView read(
      String q, String petType, String category, int page, int size, ProductSort sort) {
    return read(
        q, petType, category, null, null, List.of(), null, null, null, null, page, size, sort);
  }

  @Transactional(readOnly = true)
  public ProductListView read(
      String q,
      String petType,
      String category,
      String subcategory,
      String brand,
      List<String> facets,
      BigDecimal minPrice,
      BigDecimal maxPrice,
      Boolean subscribable,
      Boolean purchasable,
      int page,
      int size,
      ProductSort sort) {
    int offset = Math.multiplyExact(page, size);
    List<QueryParameter> parameters = new ArrayList<>();
    String where = whereClause(q, petType, category, subcategory, brand, facets, minPrice, maxPrice, subscribable, purchasable, parameters);
    Number total =
        (Number)
            bind(
                    entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM products p JOIN categories c ON c.id=p.category_id JOIN brands b ON b.id=p.brand_id LEFT JOIN categories parent ON parent.id=c.parent_id "
                            + where),
                    parameters)
                .getSingleResult();
    String order =
        switch (sort) {
          case PRICE_ASC -> " ORDER BY representative_price IS NULL ASC, representative_price ASC, p.id ASC";
          case PRICE_DESC -> " ORDER BY representative_price IS NULL ASC, representative_price DESC, p.id ASC";
          case RATING -> " ORDER BY average_rating IS NULL ASC, average_rating DESC, p.id DESC";
          case REVIEW_COUNT -> " ORDER BY review_count DESC, p.id DESC";
          case NEWEST, RECOMMENDED -> " ORDER BY p.id DESC";
        };
    String sql =
        """
        SELECT p.id product_id,p.name,p.pet_type,p.short_description,
               COALESCE(main_image.image_url,p.thumbnail_url) thumbnail_url,
               c.id category_id,c.name category_name,c.slug category_slug,
               b.id brand_id,b.name brand_name,b.slug brand_slug,b.logo_url brand_logo_url,
               (SELECT s2.price FROM skus s2 WHERE s2.product_id=p.id AND s2.status='ACTIVE' ORDER BY s2.price ASC,s2.id ASC LIMIT 1) representative_price,
               (SELECT s2.compare_at_price FROM skus s2 WHERE s2.product_id=p.id AND s2.status='ACTIVE' ORDER BY s2.price ASC,s2.id ASC LIMIT 1) compare_at_price,
               (SELECT s2.id FROM skus s2 WHERE s2.product_id=p.id AND s2.status='ACTIVE' ORDER BY s2.price ASC,s2.id ASC LIMIT 1) representative_sku_id,
               (SELECT s2.name FROM skus s2 WHERE s2.product_id=p.id AND s2.status='ACTIVE' ORDER BY s2.price ASC,s2.id ASC LIMIT 1) representative_sku_name,
               (SELECT AVG(r.rating) FROM reviews r WHERE r.product_id=p.id AND r.visible=true) average_rating,
               (SELECT COUNT(*) FROM reviews r WHERE r.product_id=p.id AND r.visible=true) review_count,
               MAX(CASE WHEN s.status='ACTIVE' AND s.subscribable=true THEN 1 ELSE 0 END) has_subscribable,
               MAX(CASE WHEN s.status='ACTIVE' AND i.available_quantity > 0 THEN 1 ELSE 0 END) purchasable
        FROM products p JOIN categories c ON c.id=p.category_id JOIN brands b ON b.id=p.brand_id
        LEFT JOIN categories parent ON parent.id=c.parent_id
        LEFT JOIN product_images main_image ON main_image.product_id=p.id AND main_image.image_type='MAIN'
        LEFT JOIN skus s ON s.product_id=p.id LEFT JOIN inventories i ON i.sku_id=s.id
        """
            + where
            + " GROUP BY p.id,p.name,p.pet_type,p.short_description,p.thumbnail_url,main_image.image_url,c.id,c.name,c.slug,b.id,b.name,b.slug,b.logo_url"
            + order
            + " LIMIT :limit OFFSET :offset";
    parameters.add(new QueryParameter("limit", size));
    parameters.add(new QueryParameter("offset", offset));
    List<Tuple> rows = bind(entityManager.createNativeQuery(sql, Tuple.class), parameters).getResultList();
    List<ProductSummary> items = rows.stream().map(this::toSummary).toList();
    return new ProductListView(items, page, size, total.longValue());
  }

  @Transactional(readOnly = true)
  public List<ProductDetailSkuRow> readDetailSkus(Long productId) {
    List<Tuple> rows =
        entityManager
            .createNativeQuery(
                "SELECT s.id,s.name,s.price,s.compare_at_price,s.subscribable,COALESCE(i.available_quantity,0) available_quantity "
                    + "FROM skus s LEFT JOIN inventories i ON i.sku_id=s.id "
                    + "WHERE s.product_id=:productId AND s.status='ACTIVE' ORDER BY s.display_order ASC,s.id ASC",
                Tuple.class)
            .setParameter("productId", productId)
            .getResultList();
    if (rows.isEmpty()) return List.of();
    LinkedHashMap<Long, List<ProductSelectedOption>> options = new LinkedHashMap<>();
    List<Tuple> optionRows =
        entityManager
            .createNativeQuery(
                "SELECT sov.sku_id,g.id group_id,g.name group_name,v.id value_id,v.value "
                    + "FROM sku_option_values sov JOIN product_option_values v ON v.id=sov.option_value_id "
                    + "JOIN product_option_groups g ON g.id=v.option_group_id "
                    + "WHERE sov.sku_id IN (SELECT id FROM skus WHERE product_id=:productId) "
                    + "ORDER BY g.display_order,v.display_order,v.id",
                Tuple.class)
            .setParameter("productId", productId)
            .getResultList();
    for (Tuple row : optionRows) {
      Long skuId = longValue(row, "sku_id");
      options
          .computeIfAbsent(skuId, ignored -> new ArrayList<>())
          .add(
              new ProductSelectedOption(
                  longValue(row, "group_id"),
                  stringValue(row, "group_name"),
                  longValue(row, "value_id"),
                  stringValue(row, "value")));
    }
    return rows.stream()
        .map(
            row ->
                new ProductDetailSkuRow(
                    longValue(row, "id"),
                    stringValue(row, "name"),
                    decimalValue(row, "price"),
                    decimalValue(row, "compare_at_price"),
                    booleanValue(row, "subscribable"),
                    intValue(row, "available_quantity"),
                    options.getOrDefault(longValue(row, "id"), List.of())))
        .toList();
  }

  @Transactional(readOnly = true)
  public ProductDetailSupplement readDetailSupplement(Long productId) {
    List<Tuple> brandRows =
        entityManager
            .createNativeQuery(
                "SELECT b.id,b.name,b.slug,b.logo_url FROM products p JOIN brands b ON b.id=p.brand_id "
                    + "WHERE p.id=:productId AND b.active=true",
                Tuple.class)
            .setParameter("productId", productId)
            .getResultList();
    if (brandRows.isEmpty()) return ProductDetailSupplement.empty();
    Tuple brandRow = brandRows.getFirst();
    BrandSummary brand =
        new BrandSummary(
            longValue(brandRow, "id"),
            stringValue(brandRow, "name"),
            stringValue(brandRow, "slug"),
            stringValue(brandRow, "logo_url"));
    List<Tuple> imageRows =
        entityManager
            .createNativeQuery(
                "SELECT id,image_url,alt_text,display_order,image_type FROM product_images WHERE product_id=:productId ORDER BY display_order,id",
                Tuple.class)
            .setParameter("productId", productId)
            .getResultList();
    List<ProductImage> images =
        imageRows
            .stream()
            .map(
                row ->
                    new ProductImage(
                        longValue(row, "id"),
                        stringValue(row, "image_url"),
                        stringValue(row, "alt_text"),
                        intValue(row, "display_order"),
                        stringValue(row, "image_type")))
            .toList();
    LinkedHashMap<Long, ProductOptionGroup> groups = new LinkedHashMap<>();
    List<Tuple> optionRows =
        entityManager
            .createNativeQuery(
                "SELECT g.id group_id,g.name group_name,g.display_order group_display_order,v.id value_id,v.value,v.display_order value_display_order "
                    + "FROM product_option_groups g LEFT JOIN product_option_values v ON v.option_group_id=g.id "
                    + "WHERE g.product_id=:productId ORDER BY g.display_order,g.id,v.display_order,v.id",
                Tuple.class)
            .setParameter("productId", productId)
            .getResultList();
    for (Tuple row : optionRows) {
      long groupId = longValue(row, "group_id");
      ProductOptionGroup old = groups.get(groupId);
      List<ProductOptionValue> values = old == null ? new ArrayList<>() : new ArrayList<>(old.values());
      Long valueId = nullableLongValue(row, "value_id");
      if (valueId != null)
        values.add(new ProductOptionValue(valueId, stringValue(row, "value"), intValue(row, "value_display_order")));
      groups.put(
          groupId,
          new ProductOptionGroup(
              groupId,
              stringValue(row, "group_name"),
              intValue(row, "group_display_order"),
              values));
    }
    return new ProductDetailSupplement(brand, images, List.copyOf(groups.values()));
  }

  private ProductSummary toSummary(Tuple row) {
    BigDecimal price = decimalValue(row, "representative_price");
    BigDecimal compareAt = decimalValue(row, "compare_at_price");
    Long representativeSkuId = nullableLongValue(row, "representative_sku_id");
    List<SkuPrice> prices =
        representativeSkuId == null
            ? List.of()
            : List.of(new SkuPrice(representativeSkuId, stringValue(row, "representative_sku_name"), price));
    return new ProductSummary(
        longValue(row, "product_id"),
        stringValue(row, "name"),
        stringValue(row, "pet_type"),
        stringValue(row, "short_description"),
        stringValue(row, "thumbnail_url"),
        new CategorySummary(longValue(row, "category_id"), stringValue(row, "category_name"), stringValue(row, "category_slug")),
        new SkuPriceSummary(prices),
        intValue(row, "has_subscribable") == 1,
        price,
        intValue(row, "purchasable") == 1,
        new BrandSummary(longValue(row, "brand_id"), stringValue(row, "brand_name"), stringValue(row, "brand_slug"), stringValue(row, "brand_logo_url")),
        compareAt,
        discountRate(price, compareAt),
        decimalValue(row, "average_rating"),
        longValue(row, "review_count"));
  }

  private String whereClause(
      String q,
      String petType,
      String category,
      String subcategory,
      String brand,
      List<String> facets,
      BigDecimal minPrice,
      BigDecimal maxPrice,
      Boolean subscribable,
      Boolean purchasable,
      List<QueryParameter> parameters) {
    StringBuilder where = new StringBuilder(" WHERE p.display_status='PUBLIC' AND c.active=true AND b.active=true");
    if (q != null && !q.isBlank()) {
      String needle = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
      String q1 = add(parameters, needle);
      String q2 = add(parameters, needle);
      String q3 = add(parameters, needle);
      where.append(" AND (LOWER(p.name) LIKE :").append(q1).append(" OR LOWER(p.short_description) LIKE :").append(q2).append(" OR LOWER(COALESCE(p.description,'')) LIKE :").append(q3).append(")");
    }
    if (petType != null && !petType.isBlank()) where.append(" AND LOWER(p.pet_type)=:").append(add(parameters, petType.trim().toLowerCase(Locale.ROOT)));
    if (category != null && !category.isBlank()) {
      String value = category.trim().toLowerCase(Locale.ROOT);
      where.append(" AND (LOWER(c.slug)=:").append(add(parameters, value)).append(" OR LOWER(parent.slug)=:").append(add(parameters, value)).append(")");
    }
    if (subcategory != null && !subcategory.isBlank()) where.append(" AND c.parent_id IS NOT NULL AND LOWER(c.slug)=:").append(add(parameters, subcategory.trim().toLowerCase(Locale.ROOT)));
    if (brand != null && !brand.isBlank()) where.append(" AND LOWER(b.slug)=:").append(add(parameters, brand.trim().toLowerCase(Locale.ROOT)));
    if (minPrice != null || maxPrice != null) {
      where.append(" AND EXISTS (SELECT 1 FROM skus sp WHERE sp.product_id=p.id AND sp.status='ACTIVE'");
      if (minPrice != null) where.append(" AND sp.price>=:").append(add(parameters, minPrice));
      if (maxPrice != null) where.append(" AND sp.price<=:").append(add(parameters, maxPrice));
      where.append(")");
    }
    if (subscribable != null)
      where.append(subscribable ? " AND EXISTS (SELECT 1 FROM skus ss WHERE ss.product_id=p.id AND ss.status='ACTIVE' AND ss.subscribable=true)" : " AND NOT EXISTS (SELECT 1 FROM skus ss WHERE ss.product_id=p.id AND ss.status='ACTIVE' AND ss.subscribable=true)");
    if (purchasable != null)
      where.append(purchasable ? " AND EXISTS (SELECT 1 FROM skus si JOIN inventories ii ON ii.sku_id=si.id WHERE si.product_id=p.id AND si.status='ACTIVE' AND ii.available_quantity>0)" : " AND NOT EXISTS (SELECT 1 FROM skus si JOIN inventories ii ON ii.sku_id=si.id WHERE si.product_id=p.id AND si.status='ACTIVE' AND ii.available_quantity>0)");
    for (String facet : facets == null ? List.<String>of() : facets) {
      String[] pair = facet == null ? new String[0] : facet.split(":", 2);
      if (pair.length != 2 || pair[0].isBlank() || pair[1].isBlank()) throw new IllegalArgumentException("facet은 key:value 형식이어야 합니다.");
      where.append(" AND EXISTS (SELECT 1 FROM product_facet_values pfv JOIN facet_options fo ON fo.id=pfv.facet_option_id JOIN facet_definitions fd ON fd.id=fo.facet_definition_id WHERE pfv.product_id=p.id AND fd.`key`=:").append(add(parameters, pair[0].trim())).append(" AND fo.value=:").append(add(parameters, pair[1].trim())).append(")");
    }
    return where.toString();
  }

  private String add(List<QueryParameter> parameters, Object value) {
    String name = "p" + parameters.size();
    parameters.add(new QueryParameter(name, value));
    return name;
  }

  private Query bind(Query query, List<QueryParameter> parameters) {
    parameters.forEach(parameter -> query.setParameter(parameter.name(), parameter.value()));
    return query;
  }

  private Integer discountRate(BigDecimal price, BigDecimal compareAt) {
    if (price == null || compareAt == null || compareAt.signum() <= 0) return null;
    return compareAt.subtract(price).multiply(BigDecimal.valueOf(100)).divide(compareAt, 0, RoundingMode.DOWN).intValue();
  }

  private static Object value(Tuple row, String alias) {
    return row.get(alias);
  }

  private static String stringValue(Tuple row, String alias) {
    return (String) value(row, alias);
  }

  private static Number numberValue(Tuple row, String alias) {
    return (Number) value(row, alias);
  }

  private static long longValue(Tuple row, String alias) {
    return numberValue(row, alias).longValue();
  }

  private static Long nullableLongValue(Tuple row, String alias) {
    Number number = (Number) value(row, alias);
    return number == null ? null : number.longValue();
  }

  private static int intValue(Tuple row, String alias) {
    return numberValue(row, alias).intValue();
  }

  private static boolean booleanValue(Tuple row, String alias) {
    Object raw = value(row, alias);
    return raw instanceof Boolean booleanValue ? booleanValue : numberValue(row, alias).intValue() != 0;
  }

  private static BigDecimal decimalValue(Tuple row, String alias) {
    Number number = (Number) value(row, alias);
    return number == null ? null : number instanceof BigDecimal decimal ? decimal : new BigDecimal(number.toString());
  }

  private record QueryParameter(String name, Object value) {}
}
