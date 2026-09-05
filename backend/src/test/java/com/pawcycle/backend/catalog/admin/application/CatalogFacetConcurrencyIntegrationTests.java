package com.pawcycle.backend.catalog.admin.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawcycle.backend.catalog.admin.api.ProductPatchRequest;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminPersistence;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
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
class CatalogFacetConcurrencyIntegrationTests {
  @Autowired private CatalogAdminPersistence expansionService;
  @Autowired private AdminCatalogService adminCatalogService;
  @Autowired private JdbcTemplate jdbc;

  private Long sourceCategoryId;
  private Long targetCategoryId;
  private Long productId;
  private Long facetDefinitionId;
  private Long facetOptionId;

  @AfterEach
  void tearDown() {
    if (productId != null) {
      jdbc.update("DELETE FROM product_images WHERE product_id=?", productId);
      jdbc.update("DELETE FROM product_facet_values WHERE product_id=?", productId);
    }
    if (sourceCategoryId != null || targetCategoryId != null) {
      jdbc.update(
          "DELETE FROM category_facets WHERE category_id IN (?,?)",
          sourceCategoryId == null ? -1L : sourceCategoryId,
          targetCategoryId == null ? -1L : targetCategoryId);
    }
    if (facetDefinitionId != null) {
      jdbc.update("DELETE FROM facet_options WHERE facet_definition_id=?", facetDefinitionId);
      jdbc.update("DELETE FROM facet_definitions WHERE id=?", facetDefinitionId);
    }
    if (productId != null) jdbc.update("DELETE FROM products WHERE id=?", productId);
    if (sourceCategoryId != null)
      jdbc.update("DELETE FROM categories WHERE id=?", sourceCategoryId);
    if (targetCategoryId != null)
      jdbc.update("DELETE FROM categories WHERE id=?", targetCategoryId);
  }

  @Test
  void concurrentProductFacetSetAndCategoryFacetRemovalPreserveInvariant() throws Exception {
    seedCatalog(false);

    ConcurrentResult results =
        runTogether(
            () ->
                expansionService.setProductFacetValues(
                    productId,
                    new com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels
                        .ProductFacetValuesCommand(List.of(facetOptionId))),
            () -> expansionService.removeCategoryFacet(sourceCategoryId, facetDefinitionId));

    assertThat(results.successCount()).isEqualTo(1);
    if (results.first().success()) {
      assertThat(results.second().code()).isEqualTo("CATEGORY_FACET_IN_USE");
    } else {
      assertThat(results.first().code()).isEqualTo("PRODUCT_FACET_NOT_ALLOWED");
      assertThat(results.second().success()).isTrue();
    }
    assertFacetInvariant();
  }

  @Test
  void concurrentCategoryChangeAndTargetFacetRemovalPreserveInvariant() throws Exception {
    seedCatalog(true);
    expansionService.setProductFacetValues(
        productId,
        new com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels
            .ProductFacetValuesCommand(List.of(facetOptionId)));

    ConcurrentResult results =
        runTogether(
            () -> {
              ProductPatchRequest patch = new ProductPatchRequest();
              patch.readCategoryId(targetCategoryId);
              adminCatalogService.updateProduct(productId, patch);
            },
            () -> expansionService.removeCategoryFacet(targetCategoryId, facetDefinitionId));

    assertThat(results.successCount()).isEqualTo(1);
    if (results.first().success()) {
      assertThat(results.second().code()).isEqualTo("CATEGORY_FACET_IN_USE");
    } else {
      assertThat(results.first().code()).isEqualTo("PRODUCT_FACET_CATEGORY_CONFLICT");
      assertThat(results.second().success()).isTrue();
    }
    assertFacetInvariant();
  }

  private void seedCatalog(boolean assignTargetFacet) {
    String suffix = Long.toUnsignedString(System.nanoTime());
    jdbc.update(
        "INSERT INTO categories(name,slug,display_order,active) VALUES (?,?,0,true)",
        "facet-source-" + suffix,
        "facet-source-" + suffix);
    sourceCategoryId = lastInsertId();
    jdbc.update(
        "INSERT INTO categories(name,slug,display_order,active) VALUES (?,?,1,true)",
        "facet-target-" + suffix,
        "facet-target-" + suffix);
    targetCategoryId = lastInsertId();
    jdbc.update(
        """
        INSERT INTO products(brand_id,catalog_key,category_id,name,short_description,description,pet_type,thumbnail_url,display_status)
        VALUES (1,?,?,?,'facet concurrency',NULL,'DOG',NULL,'DRAFT')
        """,
        "facet-product-" + suffix,
        sourceCategoryId,
        "facet-product-" + suffix);
    productId = lastInsertId();
    jdbc.update(
        "INSERT INTO facet_definitions(`key`,name) VALUES (?,?)",
        "facet-" + suffix,
        "facet-" + suffix);
    facetDefinitionId = lastInsertId();
    jdbc.update(
        "INSERT INTO facet_options(facet_definition_id,value,display_order) VALUES (?,? ,0)",
        facetDefinitionId,
        "value-" + suffix);
    facetOptionId = lastInsertId();
    jdbc.update(
        "INSERT INTO category_facets(category_id,facet_definition_id,display_order) VALUES (?,?,0)",
        sourceCategoryId,
        facetDefinitionId);
    if (assignTargetFacet) {
      jdbc.update(
          "INSERT INTO category_facets(category_id,facet_definition_id,display_order) VALUES"
              + " (?,?,0)",
          targetCategoryId,
          facetDefinitionId);
    }
  }

  private ConcurrentResult runTogether(ThrowingOperation first, ThrowingOperation second)
      throws Exception {
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Outcome> firstFuture = executor.submit(() -> executeTogether(first, ready, start));
      Future<Outcome> secondFuture = executor.submit(() -> executeTogether(second, ready, start));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      return new ConcurrentResult(
          firstFuture.get(10, TimeUnit.SECONDS), secondFuture.get(10, TimeUnit.SECONDS));
    }
  }

  private Outcome executeTogether(
      ThrowingOperation operation, CountDownLatch ready, CountDownLatch start) throws Exception {
    ready.countDown();
    if (!start.await(10, TimeUnit.SECONDS))
      throw new IllegalStateException("concurrency start timeout");
    try {
      operation.run();
      return new Outcome(true, null);
    } catch (AdminCatalogConflictException exception) {
      return new Outcome(false, exception.getCode());
    }
  }

  private void assertFacetInvariant() {
    Long invalid =
        jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM product_facet_values pfv
            JOIN products p ON p.id=pfv.product_id
            JOIN facet_options fo ON fo.id=pfv.facet_option_id
            WHERE pfv.product_id=?
              AND NOT EXISTS (
                SELECT 1
                FROM category_facets cf
                WHERE cf.category_id=p.category_id
                  AND cf.facet_definition_id=fo.facet_definition_id
              )
            """,
            Long.class,
            productId);
    assertThat(invalid).isZero();
  }

  private long lastInsertId() {
    return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }

  private record Outcome(boolean success, String code) {}

  private record ConcurrentResult(Outcome first, Outcome second) {
    int successCount() {
      return (first.success() ? 1 : 0) + (second.success() ? 1 : 0);
    }
  }

  @FunctionalInterface
  private interface ThrowingOperation {
    void run() throws Exception;
  }
}
