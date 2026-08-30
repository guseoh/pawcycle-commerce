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

        CustomerCatalogImportService.ImportResult result = customerCatalog.validate();

        assertThat(counts()).isEqualTo(before);
        assertThat(result.supplement().operation()).isEqualTo(CustomerCatalogV3ImportService.Operation.VALIDATE);
        assertThat(result.supplement().expectedBrands()).isEqualTo(9);
        assertThat(result.supplement().expectedCategories()).isEqualTo(23);
        assertThat(result.supplement().expectedProducts()).isEqualTo(68);
        assertThat(result.supplement().expectedSkus()).isEqualTo(124);
        assertThat(result.correction().operation()).isEqualTo(CustomerCatalogRealismCorrectionService.Operation.VALIDATE);
        assertThat(result.summary()).contains("CUSTOMER_CATALOG_IMPORT_RESULT status=PASS");
    }

    @Test
    void applyCreatesOneHundredProductCanonicalCatalogAndIsIdempotent() {
        CustomerCatalogImportService.ImportResult first = customerCatalog.apply();
        Map<String, Integer> afterFirst = counts();
        CustomerCatalogImportService.ImportResult second = customerCatalog.apply();

        assertThat(counts()).isEqualTo(afterFirst);
        assertThat(count("SELECT COUNT(*) FROM products")).isEqualTo(100);
        assertThat(count("SELECT COUNT(*) FROM skus")).isEqualTo(166);
        assertThat(count("SELECT COUNT(*) FROM brands")).isEqualTo(10);
        assertThat(count("SELECT COUNT(*) FROM categories WHERE slug<>'__pawcycle_uncategorized__'")).isEqualTo(27);
        assertThat(jdbc.queryForList("SELECT COUNT(*) FROM products GROUP BY pet_type ORDER BY pet_type", Integer.class))
                .containsExactly(50, 50);
        assertThat(first.supplement().operation()).isEqualTo(CustomerCatalogV3ImportService.Operation.APPLY);
        assertThat(second.supplement().productsMissing()).isZero();
        assertThat(second.supplement().skusMissing()).isZero();
        assertThat(jdbc.queryForObject("SELECT name FROM brands WHERE slug='pawcycle-demo-catalog'", String.class))
                .isEqualTo("PawCycle");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM brands WHERE name='PawCycle Demo Catalog'", Integer.class))
                .isZero();
        assertThumbnailMatchesMain("qa3-dog-daily-pad");
        assertThumbnailMatchesMain("qa3-cat-flat-scratcher");
        assertThumbnailMatchesMain("qa3-cat-curve-scratcher");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_images a JOIN product_images b ON a.product_id=b.product_id "
                        + "WHERE a.image_type='MAIN' AND b.image_type='MAIN' AND a.id<>b.id "
                        + "AND a.image_url=b.image_url AND a.product_id IN "
                        + "(SELECT id FROM products WHERE catalog_key IN ('qa3-cat-flat-scratcher','qa3-cat-curve-scratcher'))",
                Integer.class)).isZero();
    }

    @Test
    void correctionConflictRollsBackWholeCustomerCatalogTransaction() {
        customerCatalog.apply();
        jdbc.update("UPDATE brands SET name='Unexpected brand edit' WHERE slug='pawcycle-demo-catalog'");
        jdbc.update("UPDATE categories SET name='Unexpected category edit' WHERE slug='food'");

        assertThatThrownBy(customerCatalog::apply)
                .isInstanceOf(CatalogManifestImportException.class)
                .hasMessageContaining("brand pawcycle-demo-catalog");
        assertThat(jdbc.queryForObject("SELECT name FROM brands WHERE slug='pawcycle-demo-catalog'", String.class))
                .isEqualTo("Unexpected brand edit");
        assertThat(jdbc.queryForObject("SELECT name FROM categories WHERE slug='food'", String.class))
                .isEqualTo("Unexpected category edit");
    }

    @Test
    void correctionApplyPreservesMutableInventoryState() {
        customerCatalog.apply();
        jdbc.update("UPDATE inventories i JOIN skus s ON s.id=i.sku_id SET i.available_quantity=7,i.reserved_quantity=3,i.version=4 WHERE s.sku_code='DEMO-DOG-FOOD-SALMON-2KG'");

        customerCatalog.apply();

        Map<String, Object> inventory = jdbc.queryForMap("SELECT available_quantity,reserved_quantity,version FROM inventories i JOIN skus s ON s.id=i.sku_id WHERE s.sku_code='DEMO-DOG-FOOD-SALMON-2KG'");
        assertThat(((Number) inventory.get("available_quantity")).intValue()).isEqualTo(7);
        assertThat(((Number) inventory.get("reserved_quantity")).intValue()).isEqualTo(3);
        assertThat(((Number) inventory.get("version")).intValue()).isEqualTo(4);
    }

    private void assertThumbnailMatchesMain(String catalogKey) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products p JOIN product_images i ON i.product_id=p.id "
                + "WHERE p.catalog_key=? AND i.image_type='MAIN' AND p.thumbnail_url=i.image_url", Integer.class, catalogKey))
                .isEqualTo(1);
    }

    private Map<String, Integer> counts() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String table : List.of(
                "brands", "categories", "products", "skus", "inventories", "product_images",
                "product_option_groups", "product_option_values", "sku_option_values", "facet_definitions",
                "facet_options", "category_facets", "product_facet_values", "product_detail_sections")) {
            result.put(table, count("SELECT COUNT(*) FROM " + table));
        }
        return result;
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
