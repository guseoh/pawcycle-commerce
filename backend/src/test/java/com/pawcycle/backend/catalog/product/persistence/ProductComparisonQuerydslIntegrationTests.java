package com.pawcycle.backend.catalog.product.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawcycle.backend.catalog.admin.domain.FacetDefinitionEntity;
import com.pawcycle.backend.catalog.admin.domain.FacetOptionEntity;
import com.pawcycle.backend.catalog.admin.domain.ProductFacetValueEntity;
import com.pawcycle.backend.catalog.brand.domain.Brand;
import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.product.domain.Product;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
    + "com.pawcycle.backend.catalog.product.persistence.ProductComparisonQuerydslIntegrationTests$PilotSqlInspector")
@ActiveProfiles("test")
@Transactional
class ProductComparisonQuerydslIntegrationTests {
  @Autowired private EntityManager entities;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ProductComparisonQueryRepository comparisons;

  @Test
  void facetProjectionMatchesPreviousSqlIncludingTiesUnicodeAndMultipleDefinitions() {
    String unique = UUID.randomUUID().toString();
    Category category = new Category("Pilot category", "pilot-category-" + unique, 0, true);
    Brand brand = new Brand("Pilot brand", "pilot-brand-" + unique, null, true, 0);
    entities.persist(category);
    entities.persist(brand);
    Product product = new Product(category, "Querydsl pilot", "pilot", null, "DOG", null, "DRAFT");
    Product other = new Product(category, "Other pilot", "pilot", null, "DOG", null, "DRAFT");
    product.updateBrandId(brand.getId());
    other.updateBrandId(brand.getId());
    entities.persist(product);
    entities.persist(other);
    var first = new FacetDefinitionEntity("first-" + unique, "첫 번째");
    var second = new FacetDefinitionEntity("second-" + unique, "두 번째");
    entities.persist(first);
    entities.persist(second);
    var tieFirst = new FacetOptionEntity(first, "한글:값", 2);
    var tieSecond = new FacetOptionEntity(first, "", 2);
    var earlier = new FacetOptionEntity(first, "낮은 순서", 1);
    var nextDefinition = new FacetOptionEntity(second, "다른 정의", 0);
    for (var option : List.of(tieFirst, tieSecond, earlier, nextDefinition)) entities.persist(option);
    // Insertion order differs from query order; duplicate definition order is broken by option id.
    for (var option : List.of(nextDefinition, tieSecond, earlier, tieFirst)) {
      entities.persist(new ProductFacetValueEntity(product, option));
    }
    entities.persist(new ProductFacetValueEntity(other, nextDefinition));
    entities.flush();
    entities.clear();

    List<String> facets;
    PilotSqlInspector.statements.set(new ArrayList<>());
    try {
      facets = comparisons.findFacets(product.getId());
      assertThat(PilotSqlInspector.statements.get()).hasSize(1);
      assertThat(PilotSqlInspector.statements.get().getFirst())
          .contains("product_facet_values", "join facet_options", "join facet_definitions", "order by")
          .doesNotContain("for update");
    } finally {
      PilotSqlInspector.statements.remove();
    }
    assertThat(facets)
        .containsExactly(first.getKey() + ":낮은 순서", first.getKey() + ":한글:값",
            first.getKey() + ":", second.getKey() + ":다른 정의")
        .isEqualTo(previousSql(product.getId()));
    assertThat(comparisons.findFacets(other.getId())).isEqualTo(previousSql(other.getId()));
    assertThat(comparisons.findFacets(Long.MAX_VALUE)).isEqualTo(previousSql(Long.MAX_VALUE)).isEmpty();
  }

  private List<String> previousSql(long productId) {
    return jdbc.queryForList(
        "SELECT CONCAT(fd.`key`,':',fo.value) FROM product_facet_values pfv "
            + "JOIN facet_options fo ON fo.id=pfv.facet_option_id "
            + "JOIN facet_definitions fd ON fd.id=fo.facet_definition_id "
            + "WHERE pfv.product_id=? ORDER BY fd.id,fo.display_order,fo.id", String.class, productId);
  }

  public static class PilotSqlInspector implements StatementInspector {
    private static final ThreadLocal<List<String>> statements = new ThreadLocal<>();

    @Override
    public String inspect(String sql) {
      List<String> current = statements.get();
      if (current != null) current.add(sql);
      return sql;
    }
  }
}
