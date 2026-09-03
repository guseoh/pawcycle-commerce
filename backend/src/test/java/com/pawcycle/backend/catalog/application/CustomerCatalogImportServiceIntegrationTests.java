package com.pawcycle.backend.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CustomerCatalogImportServiceIntegrationTests {

  @Autowired CustomerCatalogImportService customerCatalog;
  @Autowired JdbcTemplate jdbc;

  @Test
  void validateChecksCanonicalCatalogWithoutMutatingDatabase() {
    Map<String, Integer> before = counts();

    CustomerCatalogImportResult result = customerCatalog.validate();

    assertThat(counts()).isEqualTo(before);
    assertThat(result.supplement().operation())
        .isEqualTo(CustomerCatalogImportOperation.VALIDATE);
    assertThat(result.supplement().expectedBrands()).isEqualTo(9);
    assertThat(result.supplement().expectedCategories()).isEqualTo(23);
    assertThat(result.supplement().expectedProducts()).isEqualTo(68);
    assertThat(result.supplement().expectedSkus()).isEqualTo(124);
    assertThat(result.correction().operation())
        .isEqualTo(CustomerCatalogRealismOperation.VALIDATE);
    assertThat(result.summary()).contains("CUSTOMER_CATALOG_IMPORT_RESULT status=PASS");
  }

  @Test
  void applyCreatesOneHundredProductCanonicalCatalogAndIsIdempotent() {
    CustomerCatalogImportResult first = customerCatalog.apply();
    Map<String, Integer> afterFirst = counts();
    Map<String, Object> protectedState = protectedCatalogState();
    CustomerCatalogImportResult second = customerCatalog.apply();

    assertThat(counts()).isEqualTo(afterFirst);
    assertThat(count("SELECT COUNT(*) FROM products")).isEqualTo(100);
    assertThat(count("SELECT COUNT(*) FROM skus")).isEqualTo(166);
    assertThat(count("SELECT COUNT(*) FROM brands")).isEqualTo(10);
    assertThat(count("SELECT COUNT(*) FROM categories WHERE slug<>'__pawcycle_uncategorized__'"))
        .isEqualTo(27);
    assertThat(
            jdbc.queryForList(
                "SELECT COUNT(*) FROM products GROUP BY pet_type ORDER BY pet_type", Integer.class))
        .containsExactly(50, 50);
    assertThat(first.supplement().operation())
        .isEqualTo(CustomerCatalogImportOperation.APPLY);
    assertThat(first.correction().brandsUpdated()).isEqualTo(1);
    assertThat(first.correction().productsUpdated()).isEqualTo(8);
    assertThat(first.correction().imagesUpdated()).isEqualTo(5);
    assertThat(second.supplement().productsMissing()).isZero();
    assertThat(second.supplement().skusMissing()).isZero();
    assertThat(second.correction().brandsUpdated()).isZero();
    assertThat(second.correction().productsUpdated()).isZero();
    assertThat(second.correction().imagesUpdated()).isZero();
    assertThat(protectedCatalogState()).isEqualTo(protectedState);
    assertThat(
            jdbc.queryForObject(
                "SELECT name FROM brands WHERE slug='pawcycle-demo-catalog'", String.class))
        .isEqualTo("PawCycle");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM brands WHERE name='PawCycle Demo Catalog'", Integer.class))
        .isZero();
    assertThumbnailMatchesMain("qa3-dog-daily-pad");
    assertThumbnailMatchesMain("qa3-cat-flat-scratcher");
    assertThumbnailMatchesMain("qa3-cat-curve-scratcher");
    assertCorrectedImage(
        "qa3-cat-breakaway-collar",
        "https://images.unsplash.com/photo-1714658990886-7fbb2deac266?w=800&q=80",
        "세이프 버클 캣 칼라 대표 이미지");
    assertCorrectedImage(
        "qa3-cat-photo-scarf",
        "https://images.unsplash.com/photo-1654849158272-e2bda62c16aa?w=800&q=80",
        "포토 데이 스카프 대표 이미지");
    assertThat(
            jdbc.queryForList(
                "SELECT i.image_url FROM product_images i JOIN products p ON p.id=i.product_id"
                    + " WHERE i.image_type='MAIN' AND p.catalog_key IN"
                    + " ('qa3-cat-flat-scratcher','qa3-cat-curve-scratcher') GROUP BY i.image_url"
                    + " HAVING COUNT(DISTINCT p.id) > 1",
                String.class))
        .isEmpty();
  }

  @Test
  void newImageCorrectionRejectsUnexpectedCurrentValue() {
    customerCatalog.apply();
    jdbc.update(
        "UPDATE product_images i JOIN products p ON p.id=i.product_id SET i.image_url=? WHERE"
            + " p.catalog_key=? AND i.image_type='MAIN' AND i.display_order=0",
        "https://images.unsplash.com/photo-0000000000000-unexpected?w=800&q=80",
        "qa3-cat-breakaway-collar");

    assertThatThrownBy(customerCatalog::apply)
        .isInstanceOf(CatalogManifestImportException.class)
        .hasMessageContaining("product_images collection");
  }

  @Test
  void correctionConflictRollsBackWholeCustomerCatalogTransaction() {
    customerCatalog.apply();
    jdbc.update(
        "UPDATE brands SET name='Unexpected brand edit' WHERE slug='pawcycle-demo-catalog'");

    assertThatThrownBy(customerCatalog::apply)
        .isInstanceOf(CatalogManifestImportException.class)
        .hasMessageContaining("brand pawcycle-demo-catalog");
    assertThat(
            jdbc.queryForObject(
                "SELECT name FROM brands WHERE slug='pawcycle-demo-catalog'", String.class))
        .isEqualTo("Unexpected brand edit");
  }

  @Test
  void correctionApplyPreservesMutableInventoryState() {
    customerCatalog.apply();
    jdbc.update(
        "UPDATE inventories i JOIN skus s ON s.id=i.sku_id SET"
            + " i.available_quantity=7,i.reserved_quantity=3,i.version=4 WHERE"
            + " s.sku_code='DEMO-DOG-FOOD-SALMON-2KG'");

    customerCatalog.apply();

    Map<String, Object> inventory =
        jdbc.queryForMap(
            "SELECT available_quantity,reserved_quantity,version FROM inventories i JOIN skus s ON"
                + " s.id=i.sku_id WHERE s.sku_code='DEMO-DOG-FOOD-SALMON-2KG'");
    assertThat(((Number) inventory.get("available_quantity")).intValue()).isEqualTo(7);
    assertThat(((Number) inventory.get("reserved_quantity")).intValue()).isEqualTo(3);
    assertThat(((Number) inventory.get("version")).intValue()).isEqualTo(4);
  }

  private void assertThumbnailMatchesMain(String catalogKey) {
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM products p JOIN product_images i ON i.product_id=p.id WHERE"
                    + " p.catalog_key=? AND i.image_type='MAIN' AND p.thumbnail_url=i.image_url",
                Integer.class,
                catalogKey))
        .isEqualTo(1);
  }

  private void assertCorrectedImage(String catalogKey, String imageUrl, String altText) {
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM products p JOIN product_images i ON i.product_id=p.id "
                    + "WHERE p.catalog_key=? AND i.image_type='MAIN' AND i.display_order=0",
                Integer.class,
                catalogKey))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT p.thumbnail_url FROM products p WHERE p.catalog_key=?",
                String.class,
                catalogKey))
        .isEqualTo(imageUrl);
    assertThat(
            jdbc.queryForObject(
                "SELECT i.image_url FROM product_images i JOIN products p ON p.id=i.product_id "
                    + "WHERE p.catalog_key=? AND i.image_type='MAIN' AND i.display_order=0",
                String.class,
                catalogKey))
        .isEqualTo(imageUrl);
    assertThat(
            jdbc.queryForObject(
                "SELECT i.alt_text FROM product_images i JOIN products p ON p.id=i.product_id "
                    + "WHERE p.catalog_key=? AND i.image_type='MAIN' AND i.display_order=0",
                String.class,
                catalogKey))
        .isEqualTo(altText);
  }

  private Map<String, Object> protectedCatalogState() {
    String keys = "'qa3-cat-breakaway-collar','qa3-cat-photo-scarf'";
    Map<String, Object> state = new LinkedHashMap<>();
    state.put(
        "skus",
        jdbc.queryForList(
            "SELECT s.* FROM skus s JOIN products p ON p.id=s.product_id WHERE p.catalog_key IN ("
                + keys
                + ") ORDER BY s.id"));
    state.put(
        "inventories",
        jdbc.queryForList(
            "SELECT i.* FROM inventories i JOIN skus s ON s.id=i.sku_id JOIN products p ON"
                + " p.id=s.product_id WHERE p.catalog_key IN ("
                + keys
                + ") ORDER BY i.sku_id"));
    state.put(
        "optionGroups",
        jdbc.queryForList(
            "SELECT g.* FROM product_option_groups g JOIN products p ON p.id=g.product_id WHERE"
                + " p.catalog_key IN ("
                + keys
                + ") ORDER BY g.id"));
    state.put(
        "optionValues",
        jdbc.queryForList(
            "SELECT v.* FROM product_option_values v JOIN product_option_groups g ON"
                + " g.id=v.option_group_id JOIN products p ON p.id=g.product_id WHERE p.catalog_key"
                + " IN ("
                + keys
                + ") ORDER BY v.id"));
    state.put(
        "skuOptionValues",
        jdbc.queryForList(
            "SELECT sov.* FROM sku_option_values sov JOIN skus s ON s.id=sov.sku_id JOIN products p"
                + " ON p.id=s.product_id WHERE p.catalog_key IN ("
                + keys
                + ") ORDER BY sov.sku_id,sov.option_value_id"));
    state.put(
        "facets",
        jdbc.queryForList(
            "SELECT pfv.* FROM product_facet_values pfv JOIN products p ON p.id=pfv.product_id"
                + " WHERE p.catalog_key IN ("
                + keys
                + ") ORDER BY pfv.product_id,pfv.facet_option_id"));
    state.put(
        "mainImageStructure",
        jdbc.queryForList(
            "SELECT i.id,i.product_id,i.display_order,i.image_type FROM product_images i JOIN"
                + " products p ON p.id=i.product_id WHERE p.catalog_key IN ("
                + keys
                + ") ORDER BY i.id"));
    return state;
  }

  private Map<String, Integer> counts() {
    Map<String, Integer> result = new LinkedHashMap<>();
    for (String table :
        List.of(
            "brands",
            "categories",
            "products",
            "skus",
            "inventories",
            "product_images",
            "product_option_groups",
            "product_option_values",
            "sku_option_values",
            "facet_definitions",
            "facet_options",
            "category_facets",
            "product_facet_values",
            "product_detail_sections")) {
      result.put(table, count("SELECT COUNT(*) FROM " + table));
    }
    return result;
  }

  private int count(String sql) {
    return jdbc.queryForObject(sql, Integer.class);
  }
}
