package com.pawcycle.backend.catalog.product.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductDiscoveryReader {
    private final JdbcTemplate jdbcTemplate;

    public ProductDiscoveryReader(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Transactional(readOnly = true)
    public ProductListView read(String q, String petType, String category, int page, int size, ProductSort sort) {
        return read(q, petType, category, null, null, List.of(), null, null, null, null, page, size, sort);
    }

    @Transactional(readOnly = true)
    public ProductListView read(String q, String petType, String category, String subcategory, String brand,
            List<String> facets, BigDecimal minPrice, BigDecimal maxPrice, Boolean subscribable, Boolean purchasable,
            int page, int size, ProductSort sort) {
        int offset = Math.multiplyExact(page, size);
        List<Object> parameters = new ArrayList<>();
        String where = whereClause(q, petType, category, subcategory, brand, facets, minPrice, maxPrice, subscribable, purchasable, parameters);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products p JOIN categories c ON c.id=p.category_id JOIN brands b ON b.id=p.brand_id LEFT JOIN categories parent ON parent.id=c.parent_id " + where,
                Long.class, parameters.toArray());
        String order = switch (sort) {
            case PRICE_ASC -> " ORDER BY representative_price IS NULL ASC, representative_price ASC, p.id ASC";
            case PRICE_DESC -> " ORDER BY representative_price IS NULL ASC, representative_price DESC, p.id ASC";
            case RATING -> " ORDER BY average_rating IS NULL ASC, average_rating DESC, p.id DESC";
            case REVIEW_COUNT -> " ORDER BY review_count DESC, p.id DESC";
            case NEWEST, RECOMMENDED -> " ORDER BY p.id DESC";
        };
        String sql = """
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
                """ + where + " GROUP BY p.id,p.name,p.pet_type,p.short_description,p.thumbnail_url,main_image.image_url,c.id,c.name,c.slug,b.id,b.name,b.slug,b.logo_url" + order + " LIMIT ? OFFSET ?";
        parameters.add(size); parameters.add(offset);
        List<ProductListView.ProductSummary> items = jdbcTemplate.query(sql, (rs, rowNum) -> {
            BigDecimal price = rs.getBigDecimal("representative_price");
            BigDecimal compareAt = rs.getBigDecimal("compare_at_price");
            List<ProductListView.SkuPrice> prices = price == null ? List.of() : List.of(new ProductListView.SkuPrice(rs.getLong("representative_sku_id"), rs.getString("representative_sku_name"), price));
            return new ProductListView.ProductSummary(rs.getLong("product_id"), rs.getString("name"), rs.getString("pet_type"), rs.getString("short_description"), rs.getString("thumbnail_url"),
                    new ProductListView.CategorySummary(rs.getLong("category_id"), rs.getString("category_name"), rs.getString("category_slug")), new ProductListView.SkuPriceSummary(prices),
                    rs.getInt("has_subscribable") == 1, price, rs.getInt("purchasable") == 1,
                    new ProductListView.BrandSummary(rs.getLong("brand_id"), rs.getString("brand_name"), rs.getString("brand_slug"), rs.getString("brand_logo_url")),
                    compareAt, discountRate(price, compareAt), rs.getBigDecimal("average_rating"), rs.getLong("review_count"));
        }, parameters.toArray());
        return new ProductListView(items, page, size, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public List<ProductDetailSkuRow> readDetailSkus(Long productId) {
        List<ProductDetailSkuRow> rows = jdbcTemplate.query("""
                SELECT s.id,s.name,s.price,s.compare_at_price,s.subscribable,COALESCE(i.available_quantity,0) available_quantity
                FROM skus s LEFT JOIN inventories i ON i.sku_id=s.id
                WHERE s.product_id=? AND s.status='ACTIVE' ORDER BY s.display_order ASC,s.id ASC
                """, (rs, n) -> new ProductDetailSkuRow(rs.getLong("id"), rs.getString("name"), rs.getBigDecimal("price"), rs.getBigDecimal("compare_at_price"), rs.getBoolean("subscribable"), rs.getInt("available_quantity"), List.of()), productId);
        if (rows.isEmpty()) return rows;
        Map<Long, List<ProductDetailView.SelectedOption>> options = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT sov.sku_id,g.id group_id,g.name group_name,v.id value_id,v.value
                FROM sku_option_values sov JOIN product_option_values v ON v.id=sov.option_value_id
                JOIN product_option_groups g ON g.id=v.option_group_id
                WHERE sov.sku_id IN (SELECT id FROM skus WHERE product_id=?) ORDER BY g.display_order,v.display_order,v.id
                """, (RowCallbackHandler) rs -> options.computeIfAbsent(rs.getLong("sku_id"), ignored -> new ArrayList<>()).add(new ProductDetailView.SelectedOption(rs.getLong("group_id"), rs.getString("group_name"), rs.getLong("value_id"), rs.getString("value"))), productId);
        return rows.stream().map(row -> new ProductDetailSkuRow(row.skuId(), row.skuName(), row.price(), row.compareAtPrice(), row.subscribable(), row.availableQuantity(), options.getOrDefault(row.skuId(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public ProductDetailSupplement readDetailSupplement(Long productId) {
        List<ProductDetailView.BrandSummary> brands = jdbcTemplate.query("""
                SELECT b.id,b.name,b.slug,b.logo_url FROM products p JOIN brands b ON b.id=p.brand_id
                WHERE p.id=? AND b.active=true
                """, (rs, n) -> new ProductDetailView.BrandSummary(rs.getLong("id"), rs.getString("name"), rs.getString("slug"), rs.getString("logo_url")), productId);
        if (brands.isEmpty()) return ProductDetailSupplement.empty();
        List<ProductDetailView.Image> images = jdbcTemplate.query("SELECT id,image_url,alt_text,display_order,image_type FROM product_images WHERE product_id=? ORDER BY display_order,id",
                (rs, n) -> new ProductDetailView.Image(rs.getLong("id"), rs.getString("image_url"), rs.getString("alt_text"), rs.getInt("display_order"), rs.getString("image_type")), productId);
        Map<Long, ProductDetailView.OptionGroup> groups = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT g.id group_id,g.name group_name,g.display_order group_display_order,v.id value_id,v.value,v.display_order value_display_order
                FROM product_option_groups g LEFT JOIN product_option_values v ON v.option_group_id=g.id
                WHERE g.product_id=? ORDER BY g.display_order,g.id,v.display_order,v.id
                """, (RowCallbackHandler) rs -> {
                    long groupId = rs.getLong("group_id");
                    ProductDetailView.OptionGroup old = groups.get(groupId);
                    List<ProductDetailView.OptionValue> values = old == null ? new ArrayList<>() : new ArrayList<>(old.values());
                    Long valueId = rs.getObject("value_id", Long.class);
                    if (valueId != null) values.add(new ProductDetailView.OptionValue(valueId, rs.getString("value"), rs.getInt("value_display_order")));
                    groups.put(groupId, new ProductDetailView.OptionGroup(groupId, rs.getString("group_name"), rs.getInt("group_display_order"), values));
                }, productId);
        return new ProductDetailSupplement(brands.getFirst(), images, List.copyOf(groups.values()));
    }

    private String whereClause(String q, String petType, String category, String subcategory, String brand, List<String> facets,
            BigDecimal minPrice, BigDecimal maxPrice, Boolean subscribable, Boolean purchasable, List<Object> p) {
        StringBuilder where = new StringBuilder(" WHERE p.display_status='PUBLIC' AND c.active=true AND b.active=true");
        if (q != null && !q.isBlank()) { String needle = "%" + q.trim().toLowerCase(java.util.Locale.ROOT) + "%"; where.append(" AND (LOWER(p.name) LIKE ? OR LOWER(p.short_description) LIKE ? OR LOWER(COALESCE(p.description,'')) LIKE ?)"); p.add(needle); p.add(needle); p.add(needle); }
        if (petType != null && !petType.isBlank()) { where.append(" AND LOWER(p.pet_type)=?"); p.add(petType.trim().toLowerCase(java.util.Locale.ROOT)); }
        if (category != null && !category.isBlank()) { where.append(" AND (LOWER(c.slug)=? OR LOWER(parent.slug)=?)"); String value=category.trim().toLowerCase(java.util.Locale.ROOT); p.add(value);p.add(value); }
        if (subcategory != null && !subcategory.isBlank()) { where.append(" AND c.parent_id IS NOT NULL AND LOWER(c.slug)=?"); p.add(subcategory.trim().toLowerCase(java.util.Locale.ROOT)); }
        if (brand != null && !brand.isBlank()) { where.append(" AND LOWER(b.slug)=?"); p.add(brand.trim().toLowerCase(java.util.Locale.ROOT)); }
        if (minPrice != null) { where.append(" AND EXISTS (SELECT 1 FROM skus sp WHERE sp.product_id=p.id AND sp.status='ACTIVE' AND sp.price>=?)"); p.add(minPrice); }
        if (maxPrice != null) { where.append(" AND EXISTS (SELECT 1 FROM skus sp WHERE sp.product_id=p.id AND sp.status='ACTIVE' AND sp.price<=?)"); p.add(maxPrice); }
        if (subscribable != null) { where.append(subscribable ? " AND EXISTS (SELECT 1 FROM skus ss WHERE ss.product_id=p.id AND ss.status='ACTIVE' AND ss.subscribable=true)" : " AND NOT EXISTS (SELECT 1 FROM skus ss WHERE ss.product_id=p.id AND ss.status='ACTIVE' AND ss.subscribable=true)"); }
        if (purchasable != null) { where.append(purchasable ? " AND EXISTS (SELECT 1 FROM skus si JOIN inventories ii ON ii.sku_id=si.id WHERE si.product_id=p.id AND si.status='ACTIVE' AND ii.available_quantity>0)" : " AND NOT EXISTS (SELECT 1 FROM skus si JOIN inventories ii ON ii.sku_id=si.id WHERE si.product_id=p.id AND si.status='ACTIVE' AND ii.available_quantity>0)"); }
        for (String facet : facets == null ? List.<String>of() : facets) {
            String[] pair = facet == null ? new String[0] : facet.split(":", 2);
            if (pair.length != 2 || pair[0].isBlank() || pair[1].isBlank()) throw new IllegalArgumentException("facet은 key:value 형식이어야 합니다.");
            where.append(" AND EXISTS (SELECT 1 FROM product_facet_values pfv JOIN facet_options fo ON fo.id=pfv.facet_option_id JOIN facet_definitions fd ON fd.id=fo.facet_definition_id WHERE pfv.product_id=p.id AND fd.`key`=? AND fo.value=?)"); p.add(pair[0].trim()); p.add(pair[1].trim());
        }
        return where.toString();
    }

    private Integer discountRate(BigDecimal price, BigDecimal compareAt) {
        if (price == null || compareAt == null || compareAt.signum() <= 0) return null;
        return compareAt.subtract(price).multiply(BigDecimal.valueOf(100)).divide(compareAt, 0, RoundingMode.DOWN).intValue();
    }

    public record ProductDetailSkuRow(Long skuId, String skuName, BigDecimal price, BigDecimal compareAtPrice, boolean subscribable, int availableQuantity, List<ProductDetailView.SelectedOption> selectedOptions) {}
    public record ProductDetailSupplement(ProductDetailView.BrandSummary brand, List<ProductDetailView.Image> images, List<ProductDetailView.OptionGroup> optionGroups) {
        static ProductDetailSupplement empty() { return new ProductDetailSupplement(null, List.of(), List.of()); }
    }
}
