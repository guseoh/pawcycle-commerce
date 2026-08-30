package com.pawcycle.backend.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;

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
