package com.pawcycle.backend.catalog.discovery.persistence;

import com.pawcycle.backend.catalog.discovery.application.CatalogBrandResponse;
import com.pawcycle.backend.catalog.discovery.application.CatalogCategoryFacetsResponse;
import com.pawcycle.backend.catalog.discovery.application.CatalogFacetOptionResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogDiscoveryQueryRepository {
  private static final String SYSTEM_UNCATEGORIZED_SLUG = "__pawcycle_uncategorized__";
  private final JdbcTemplate jdbc;

  public CatalogDiscoveryQueryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<CategoryRow> findCategories() {
    return jdbc.query(
        "SELECT c.id,c.parent_id,c.name,c.slug,c.display_order FROM categories c"
            + " LEFT JOIN categories parent ON parent.id=c.parent_id WHERE c.active=true"
            + " AND c.slug<>? AND (c.parent_id IS NULL OR (parent.active=true AND"
            + " parent.slug<>? AND parent.parent_id IS NULL)) ORDER BY c.display_order ASC,c.id ASC",
        (rs, rowNum) ->
            new CategoryRow(
                rs.getLong("id"),
                rs.getObject("parent_id", Long.class),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getInt("display_order")),
        SYSTEM_UNCATEGORIZED_SLUG,
        SYSTEM_UNCATEGORIZED_SLUG);
  }

  public List<CatalogBrandResponse> findBrands() {
    return jdbc.query(
        "SELECT id,name,slug,logo_url,display_order FROM brands WHERE active=true"
            + " ORDER BY display_order ASC,id ASC",
        (rs, rowNum) ->
            new CatalogBrandResponse(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getString("logo_url"),
                rs.getInt("display_order")));
  }

  public List<CatalogCategoryFacetsResponse> findCategoryFacets() {
    Map<Long, CategoryFacetAccumulator> categories = new LinkedHashMap<>();
    jdbc.query(
        "SELECT c.id category_id,c.slug category_slug,cf.display_order facet_order,"
            + " fd.id facet_id,fd.`key` facet_key,fd.name facet_name,fo.id option_id,"
            + " fo.value option_value,fo.display_order option_order FROM category_facets cf"
            + " JOIN categories c ON c.id=cf.category_id LEFT JOIN categories parent ON parent.id=c.parent_id"
            + " JOIN facet_definitions fd ON fd.id=cf.facet_definition_id LEFT JOIN facet_options fo"
            + " ON fo.facet_definition_id=fd.id WHERE c.active=true AND c.slug<>? AND"
            + " (c.parent_id IS NULL OR (parent.active=true AND parent.slug<>? AND parent.parent_id IS NULL))"
            + " ORDER BY c.display_order ASC,c.id ASC,cf.display_order ASC,fd.id ASC,fo.display_order ASC,fo.id ASC",
        (RowCallbackHandler)
            rs -> {
              long categoryId = rs.getLong("category_id");
              String categorySlug = rs.getString("category_slug");
              CategoryFacetAccumulator category =
                  categories.computeIfAbsent(categoryId, ignored -> new CategoryFacetAccumulator(categorySlug));
              long facetId = rs.getLong("facet_id");
              String facetKey = rs.getString("facet_key");
              String facetName = rs.getString("facet_name");
              int facetDisplayOrder = rs.getInt("facet_order");
              FacetAccumulator facet =
                  category.facets.computeIfAbsent(
                      facetId,
                      ignored ->
                          new FacetAccumulator(facetKey, facetName, facetDisplayOrder));
              Long optionId = rs.getObject("option_id", Long.class);
              if (optionId != null) {
                facet.options.add(
                    new CatalogFacetOptionResponse(
                        optionId, rs.getString("option_value"), rs.getInt("option_order")));
              }
            },
        SYSTEM_UNCATEGORIZED_SLUG,
        SYSTEM_UNCATEGORIZED_SLUG);
    return categories.values().stream()
        .map(
            category ->
                new CatalogCategoryFacetsResponse(
                    category.categorySlug,
                    category.facets.values().stream()
                        .map(
                            facet ->
                                new com.pawcycle.backend.catalog.discovery.application.CatalogFacetResponse(
                                    facet.key, facet.name, facet.displayOrder, facet.options))
                        .toList()))
        .toList();
  }

  public record CategoryRow(Long categoryId, Long parentId, String name, String slug, int displayOrder) {}

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
    private final List<CatalogFacetOptionResponse> options = new ArrayList<>();

    private FacetAccumulator(String key, String name, int displayOrder) {
      this.key = key;
      this.name = name;
      this.displayOrder = displayOrder;
    }
  }
}
