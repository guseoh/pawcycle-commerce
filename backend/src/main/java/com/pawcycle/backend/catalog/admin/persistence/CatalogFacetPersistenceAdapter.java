package com.pawcycle.backend.catalog.admin.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Owns the category/product facet compatibility query used by catalog mutations. */
@Repository
public class CatalogFacetPersistenceAdapter {
  private final JdbcTemplate queries;

  public CatalogFacetPersistenceAdapter(JdbcTemplate queries) {
    this.queries = queries;
  }

  public boolean hasIncompatibleProductValues(long productId, long categoryId) {
    Long invalid =
        queries.queryForObject(
            """
            SELECT COUNT(*)
            FROM product_facet_values pfv
            JOIN facet_options fo ON fo.id=pfv.facet_option_id
            WHERE pfv.product_id=?
              AND NOT EXISTS (
                SELECT 1 FROM category_facets cf
                WHERE cf.category_id=? AND cf.facet_definition_id=fo.facet_definition_id
              )
            """,
            Long.class,
            productId,
            categoryId);
    return invalid != null && invalid > 0;
  }
}
