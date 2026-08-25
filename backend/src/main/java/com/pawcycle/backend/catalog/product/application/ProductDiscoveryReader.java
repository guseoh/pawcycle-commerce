package com.pawcycle.backend.catalog.product.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductDiscoveryReader {
	private final JdbcTemplate jdbcTemplate;

	public ProductDiscoveryReader(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public ProductListView read(String q, String petType, String category, int page, int size, ProductSort sort) {
		int offset = Math.multiplyExact(page, size);
		List<Object> parameters = new ArrayList<>();
		String where = whereClause(q, petType, category, parameters);
		long total = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM products p JOIN categories c ON c.id=p.category_id " + where,
				Long.class, parameters.toArray());
		String order = switch (sort) {
			case PRICE_ASC -> " ORDER BY representative_price IS NULL ASC, representative_price ASC, p.id ASC";
			case PRICE_DESC -> " ORDER BY representative_price IS NULL ASC, representative_price DESC, p.id ASC";
			case NEWEST -> " ORDER BY p.id DESC";
		};
		String sql = """
				SELECT p.id product_id,p.name,p.pet_type,p.short_description,p.thumbnail_url,
				       c.id category_id,c.name category_name,c.slug category_slug,
				       (SELECT s2.price FROM skus s2 WHERE s2.product_id=p.id AND s2.status='ACTIVE' ORDER BY s2.price ASC,s2.id ASC LIMIT 1) representative_price,
				       (SELECT s2.id FROM skus s2 WHERE s2.product_id=p.id AND s2.status='ACTIVE' ORDER BY s2.price ASC,s2.id ASC LIMIT 1) representative_sku_id,
				       (SELECT s2.name FROM skus s2 WHERE s2.product_id=p.id AND s2.status='ACTIVE' ORDER BY s2.price ASC,s2.id ASC LIMIT 1) representative_sku_name,
				       MAX(CASE WHEN s.status='ACTIVE' AND s.subscribable=true THEN 1 ELSE 0 END) has_subscribable,
				       MAX(CASE WHEN s.status='ACTIVE' AND i.available_quantity > 0 THEN 1 ELSE 0 END) purchasable
				FROM products p
				JOIN categories c ON c.id=p.category_id
				LEFT JOIN skus s ON s.product_id=p.id
				LEFT JOIN inventories i ON i.sku_id=s.id
				""" + where + " GROUP BY p.id,p.name,p.pet_type,p.short_description,p.thumbnail_url,c.id,c.name,c.slug" + order
				+ " LIMIT ? OFFSET ?";
		parameters.add(size);
		parameters.add(offset);
		List<ProductListView.ProductSummary> items = jdbcTemplate.query(sql, (rs, rowNum) -> {
			BigDecimal price = rs.getBigDecimal("representative_price");
			List<ProductListView.SkuPrice> prices = price == null
					? List.of()
					: List.of(new ProductListView.SkuPrice(
							rs.getLong("representative_sku_id"),
							rs.getString("representative_sku_name"), price));
			return new ProductListView.ProductSummary(
					rs.getLong("product_id"), rs.getString("name"), rs.getString("pet_type"),
					rs.getString("short_description"), rs.getString("thumbnail_url"),
					new ProductListView.CategorySummary(rs.getLong("category_id"), rs.getString("category_name"), rs.getString("category_slug")),
					new ProductListView.SkuPriceSummary(prices), rs.getInt("has_subscribable") == 1,
					price, rs.getInt("purchasable") == 1);
		}, parameters.toArray());
		return new ProductListView(items, page, size, total);
	}

	@Transactional(readOnly = true)
	public List<ProductDetailSkuRow> readDetailSkus(Long productId) {
		return jdbcTemplate.query(
				"""
				SELECT s.id, s.name, s.price, s.subscribable,
				       COALESCE(i.available_quantity, 0) AS available_quantity
				FROM skus s
				LEFT JOIN inventories i ON i.sku_id = s.id
				WHERE s.product_id = ?
				  AND s.status = 'ACTIVE'
				ORDER BY s.display_order ASC, s.id ASC
				""",
				(rs, rowNum) -> new ProductDetailSkuRow(
						rs.getLong("id"),
						rs.getString("name"),
						rs.getBigDecimal("price"),
						rs.getBoolean("subscribable"),
						rs.getInt("available_quantity")),
				productId);
	}

	private String whereClause(String q, String petType, String category, List<Object> parameters) {
		StringBuilder where = new StringBuilder(" WHERE p.display_status='PUBLIC' AND c.active=true");
		if (q != null && !q.isBlank()) {
			where.append(" AND (LOWER(p.name) LIKE ? OR LOWER(p.short_description) LIKE ? OR LOWER(COALESCE(p.description,'')) LIKE ?)");
			String needle = "%" + q.trim().toLowerCase(java.util.Locale.ROOT) + "%";
			parameters.add(needle); parameters.add(needle); parameters.add(needle);
		}
		if (petType != null && !petType.isBlank()) {
			where.append(" AND LOWER(p.pet_type)=?");
			parameters.add(petType.trim().toLowerCase(java.util.Locale.ROOT));
		}
		if (category != null && !category.isBlank()) {
			where.append(" AND LOWER(c.slug)=?");
			parameters.add(category.trim().toLowerCase(java.util.Locale.ROOT));
		}
		return where.toString();
	}

	public record ProductDetailSkuRow(
			Long skuId,
			String skuName,
			BigDecimal price,
			boolean subscribable,
			int availableQuantity) {}
}
