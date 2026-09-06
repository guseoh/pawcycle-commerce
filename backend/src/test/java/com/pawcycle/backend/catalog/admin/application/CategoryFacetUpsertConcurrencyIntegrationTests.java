package com.pawcycle.backend.catalog.admin.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.CategoryFacetAssignCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminPersistence;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CategoryFacetUpsertConcurrencyIntegrationTests {
  @Autowired private CatalogAdminPersistence catalog;
  @Autowired private JdbcTemplate jdbc;

  private Long categoryId;
  private Long definitionId;

  @AfterEach
  void tearDown() {
    if (categoryId != null && definitionId != null) {
      jdbc.update(
          "DELETE FROM category_facets WHERE category_id=? AND facet_definition_id=?",
          categoryId,
          definitionId);
    }
    if (definitionId != null) jdbc.update("DELETE FROM facet_definitions WHERE id=?", definitionId);
    if (categoryId != null) jdbc.update("DELETE FROM categories WHERE id=?", categoryId);
  }

  @Test
  void concurrentAssignmentsPreserveAtomicUpsertContract() throws Exception {
    String suffix = Long.toUnsignedString(System.nanoTime());
    jdbc.update(
        "INSERT INTO categories(name,slug,display_order,active) VALUES (?,?,0,true)",
        "upsert-category-" + suffix,
        "upsert-category-" + suffix);
    categoryId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update(
        "INSERT INTO facet_definitions(`key`,name) VALUES (?,?)",
        "upsert-" + suffix,
        "upsert-" + suffix);
    definitionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<?> first = executor.submit(() -> assignTogether(1, ready, start));
      Future<?> second = executor.submit(() -> assignTogether(2, ready, start));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      first.get(10, TimeUnit.SECONDS);
      second.get(10, TimeUnit.SECONDS);
    }

    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM category_facets WHERE category_id=? AND facet_definition_id=?",
                Long.class,
                categoryId,
                definitionId))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "SELECT display_order FROM category_facets WHERE category_id=? AND facet_definition_id=?",
                Integer.class,
                categoryId,
                definitionId))
        .isIn(1, 2);
  }

  private void assignTogether(int displayOrder, CountDownLatch ready, CountDownLatch start) {
    ready.countDown();
    try {
      if (!start.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("concurrency start timeout");
      }
      catalog.assignCategoryFacet(
          categoryId, definitionId, new CategoryFacetAssignCommand(displayOrder));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }
}
