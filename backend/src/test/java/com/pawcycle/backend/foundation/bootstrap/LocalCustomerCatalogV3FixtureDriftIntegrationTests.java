package com.pawcycle.backend.foundation.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawcycle.backend.catalog.admin.application.CatalogExpansionAdminService;
import com.pawcycle.backend.catalog.application.DemoCatalogManifestImportService;
import com.pawcycle.backend.catalog.application.DemoProductDetailSectionFixtureService;
import com.pawcycle.backend.catalog.product.application.ProductListCacheInvalidator;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Import(LocalCustomerCatalogV3FixtureDriftIntegrationTests.FixtureConfiguration.class)
@Transactional
class LocalCustomerCatalogV3FixtureDriftIntegrationTests {
  @Autowired JdbcTemplate jdbc;
  @Autowired LocalCustomerCatalogV3FixtureService fixture;

  @Test
  void changedImageDisplayOrderIsConflictAndDoesNotCreateDuplicate() {
    fixture.bootstrap();
    long productId = productId("qa3-dog-salmon-small");
    long imageId =
        jdbc.queryForObject(
            """
            SELECT id FROM product_images
            WHERE product_id=? AND image_type='DETAIL'
            ORDER BY display_order,id LIMIT 1
            """,
            Long.class,
            productId);
    int imageCount = count("SELECT COUNT(*) FROM product_images WHERE product_id=" + productId);
    jdbc.update("UPDATE product_images SET display_order=99 WHERE id=?", imageId);

    assertThatThrownBy(fixture::bootstrap)
        .isInstanceOf(LocalQaBootstrapException.class)
        .hasMessageContaining("product_images collection");
    assertThat(count("SELECT COUNT(*) FROM product_images WHERE product_id=" + productId))
        .isEqualTo(imageCount);
  }

  @Test
  void changedDetailSectionDisplayOrderIsConflictAndDoesNotCreateDuplicate() {
    fixture.bootstrap();
    long productId = productId("qa3-dog-salmon-small");
    long sectionId =
        jdbc.queryForObject(
            """
            SELECT id FROM product_detail_sections
            WHERE product_id=?
            ORDER BY display_order,id LIMIT 1
            """,
            Long.class,
            productId);
    int sectionCount =
        count("SELECT COUNT(*) FROM product_detail_sections WHERE product_id=" + productId);
    jdbc.update("UPDATE product_detail_sections SET display_order=99 WHERE id=?", sectionId);

    assertThatThrownBy(fixture::bootstrap)
        .isInstanceOf(LocalQaBootstrapException.class)
        .hasMessageContaining("product_detail_sections collection");
    assertThat(count("SELECT COUNT(*) FROM product_detail_sections WHERE product_id=" + productId))
        .isEqualTo(sectionCount);
  }

  private long productId(String key) {
    return jdbc.queryForObject("SELECT id FROM products WHERE catalog_key=?", Long.class, key);
  }

  private int count(String sql) {
    return jdbc.queryForObject(sql, Integer.class);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FixtureConfiguration {
    @Bean
    LocalCustomerCatalogV3FixtureService customerCatalogV3UnderTest(
        NativeQueryExecutor jdbcExecutor,
        DemoCatalogManifestImportService importer,
        CatalogExpansionAdminService expansion,
        ProductListCacheInvalidator cache,
        Validator validator) {
      var detail =
          new DemoProductDetailSectionFixtureService(
              jdbcExecutor, "classpath:catalog/demo-product-detail-sections.json");
      return new LocalCustomerCatalogV3FixtureService(
          jdbcExecutor,
          new LocalCommerceDemoFixtureService(importer, detail),
          expansion,
          cache,
          validator);
    }
  }
}
