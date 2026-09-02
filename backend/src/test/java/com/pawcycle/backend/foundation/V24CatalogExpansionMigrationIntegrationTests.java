package com.pawcycle.backend.foundation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class V24CatalogExpansionMigrationIntegrationTests {

  @Autowired private JdbcTemplate jdbc;

  @Test
  void v24BackfillsBrandAndAddsCatalogExpansionConstraints() throws Throwable {
    {
      jdbc.update(
          "INSERT INTO categories(name,slug,display_order,active) VALUES ('V24','v24',0,true)");
      long categoryId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
      jdbc.update(
          "INSERT INTO"
              + " products(brand_id,catalog_key,category_id,name,short_description,pet_type,display_status)"
              + " VALUES (1,'v24-product',?,'V24','V24','DOG','PUBLIC')",
          categoryId);
      long productId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
      assertThat(
              jdbc.queryForObject(
                  "SELECT column_default FROM information_schema.columns WHERE"
                      + " table_schema=DATABASE() AND table_name='products' AND"
                      + " column_name='brand_id'",
                  String.class))
          .isNull();
      jdbc.update(
          "INSERT INTO skus(product_id,sku_code,name,price,subscribable,display_order,status)"
              + " VALUES (?,'V24-SKU','V24',1000,true,0,'ACTIVE')",
          productId);

      assertThat(
              jdbc.queryForObject(
                  "SELECT brand_id FROM products WHERE id=?", Long.class, productId))
          .isEqualTo(1L);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM brands WHERE slug='pawcycle-demo-catalog'", Integer.class))
          .isEqualTo(1);
      for (String table :
          new String[] {
            "product_images",
            "product_option_groups",
            "product_option_values",
            "sku_option_values",
            "facet_definitions",
            "facet_options",
            "category_facets",
            "product_facet_values"
          }) {
        assertThat(
                jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()"
                        + " AND table_name=?",
                    Integer.class,
                    table))
            .isEqualTo(1);
      }
      jdbc.update(
          "INSERT INTO product_images(product_id,image_url,display_order,image_type) VALUES"
              + " (?,'https://example.test/main',0,'MAIN')",
          productId);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "INSERT INTO product_images(product_id,image_url,display_order,image_type)"
                          + " VALUES (?,'https://example.test/second',1,'MAIN')",
                      productId))
          .isInstanceOf(Exception.class);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "UPDATE skus SET compare_at_price=price WHERE product_id=?", productId))
          .isInstanceOf(Exception.class);
    }
  }
}
