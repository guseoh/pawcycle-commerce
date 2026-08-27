package com.pawcycle.backend.catalog.discovery.application;

import com.pawcycle.backend.catalog.discovery.api.CatalogDiscoveryView;
import com.pawcycle.backend.catalog.discovery.api.CatalogDiscoveryView.Brand;
import com.pawcycle.backend.catalog.discovery.api.CatalogDiscoveryView.Category;
import com.pawcycle.backend.catalog.discovery.api.CatalogDiscoveryView.CategoryFacets;
import com.pawcycle.backend.catalog.discovery.api.CatalogDiscoveryView.Child;
import com.pawcycle.backend.catalog.discovery.api.CatalogDiscoveryView.Facet;
import com.pawcycle.backend.catalog.discovery.api.CatalogDiscoveryView.Option;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogDiscoveryQueryService {
	private static final String SYSTEM_UNCATEGORIZED_SLUG = "__pawcycle_uncategorized__";

	private final JdbcTemplate jdbcTemplate;

	public CatalogDiscoveryQueryService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public CatalogDiscoveryView findPublicDiscovery() {
		List<CategoryRow> categories = readCategories();
		return new CatalogDiscoveryView(
				toHierarchy(categories),
				readBrands(),
				readCategoryFacets());
	}

	private List<CategoryRow> readCategories() {
		return jdbcTemplate.query("""
				SELECT c.id,c.parent_id,c.name,c.slug,c.display_order
				FROM categories c
				LEFT JOIN categories parent ON parent.id=c.parent_id
				WHERE c.active=true
				  AND c.slug<>?
				  AND (c.parent_id IS NULL OR (parent.active=true AND parent.slug<>? AND parent.parent_id IS NULL))
				ORDER BY c.display_order ASC,c.id ASC
				""", (rs, rowNum) -> new CategoryRow(
					 rs.getLong("id"),
					 rs.getObject("parent_id", Long.class),
					 rs.getString("name"),
					 rs.getString("slug"),
					 rs.getInt("display_order")),
				SYSTEM_UNCATEGORIZED_SLUG, SYSTEM_UNCATEGORIZED_SLUG);
	}

	private List<Category> toHierarchy(List<CategoryRow> rows) {
		Map<Long, List<CategoryRow>> childrenByParent = new LinkedHashMap<>();
		for (CategoryRow row : rows) {
			if (row.parentId() != null) {
				childrenByParent.computeIfAbsent(row.parentId(), ignored -> new ArrayList<>()).add(row);
			}
		}
		return rows.stream()
				.filter(row -> row.parentId() == null)
				.map(row -> new Category(
						row.categoryId(),
						row.name(),
						row.slug(),
						row.displayOrder(),
						childrenByParent.getOrDefault(row.categoryId(), List.of()).stream()
								.map(child -> new Child(child.categoryId(), child.name(), child.slug(), child.displayOrder()))
								.toList()))
				.toList();
	}

	private List<Brand> readBrands() {
		return jdbcTemplate.query("""
				SELECT id,name,slug,logo_url,display_order
				FROM brands
				WHERE active=true
				ORDER BY display_order ASC,id ASC
				""", (rs, rowNum) -> new Brand(
					rs.getLong("id"),
					rs.getString("name"),
					rs.getString("slug"),
					rs.getString("logo_url"),
					rs.getInt("display_order")));
	}

	private List<CategoryFacets> readCategoryFacets() {
		Map<Long, CategoryFacetAccumulator> categories = new LinkedHashMap<>();
		jdbcTemplate.query("""
				SELECT c.id category_id,c.slug category_slug,c.display_order category_order,
				       cf.display_order facet_order,fd.id facet_id,fd.`key` facet_key,fd.name facet_name,
				       fo.id option_id,fo.value option_value,fo.display_order option_order
				FROM category_facets cf
				JOIN categories c ON c.id=cf.category_id
				LEFT JOIN categories parent ON parent.id=c.parent_id
				JOIN facet_definitions fd ON fd.id=cf.facet_definition_id
				LEFT JOIN facet_options fo ON fo.facet_definition_id=fd.id
				WHERE c.active=true
				  AND c.slug<>?
				  AND (c.parent_id IS NULL OR (parent.active=true AND parent.slug<>? AND parent.parent_id IS NULL))
				ORDER BY c.display_order ASC,c.id ASC,cf.display_order ASC,fd.id ASC,
				         fo.display_order ASC,fo.id ASC
				""", rs -> {
					long categoryId = rs.getLong("category_id");
					String categorySlug = rs.getString("category_slug");
					CategoryFacetAccumulator category = categories.computeIfAbsent(categoryId,
							ignored -> new CategoryFacetAccumulator(categorySlug));
					long facetId = rs.getLong("facet_id");
					String facetKey = rs.getString("facet_key");
					String facetName = rs.getString("facet_name");
					int facetDisplayOrder = rs.getInt("facet_order");
					FacetAccumulator facet = category.facets.computeIfAbsent(facetId,
							ignored -> new FacetAccumulator(facetKey, facetName, facetDisplayOrder));
					Long optionId = rs.getObject("option_id", Long.class);
					if (optionId != null) {
						facet.options.add(new Option(optionId, rs.getString("option_value"), rs.getInt("option_order")));
					}
				}, SYSTEM_UNCATEGORIZED_SLUG, SYSTEM_UNCATEGORIZED_SLUG);
		return categories.values().stream()
				.map(category -> new CategoryFacets(
						category.categorySlug,
						category.facets.values().stream()
								.map(facet -> new Facet(facet.key, facet.name, facet.displayOrder, facet.options))
								.toList()))
				.toList();
	}

	private record CategoryRow(Long categoryId, Long parentId, String name, String slug, int displayOrder) {
	}

	private static final class CategoryFacetAccumulator {
		private final String categorySlug;
		private final Map<Long, FacetAccumulator> facets = new LinkedHashMap<>();

		private CategoryFacetAccumulator(String categorySlug) {
			this.categorySlug = categorySlug;
		}
	}

	private static final class FacetAccumulator {
		private final String key;
		private final String name;
		private final int displayOrder;
		private final List<Option> options = new ArrayList<>();

		private FacetAccumulator(String key, String name, int displayOrder) {
			this.key = key;
			this.name = name;
			this.displayOrder = displayOrder;
		}
	}
}
