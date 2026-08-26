package com.pawcycle.backend.foundation.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawcycle.backend.catalog.application.CatalogManifestImportException;
import com.pawcycle.backend.catalog.application.DemoCatalogManifestImportService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LocalCommerceDemoFixtureServiceIntegrationTests {

	private final JdbcTemplate jdbcTemplate;
	private final LocalCommerceDemoFixtureService fixtureService;
	private final DemoCatalogManifestImportService importService;

	@Autowired
	LocalCommerceDemoFixtureServiceIntegrationTests(
			JdbcTemplate jdbcTemplate,
			DemoCatalogManifestImportService importService) {
		this.jdbcTemplate = jdbcTemplate;
		this.importService = importService;
		this.fixtureService = new LocalCommerceDemoFixtureService(importService);
	}

	@Test
	void dryRunValidatesFreshDatabaseWithoutMutation() {
		int categoriesBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM categories", Integer.class);
		int productsBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Integer.class);
		int skusBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM skus", Integer.class);
		int inventoriesBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventories", Integer.class);
		int plansBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM subscription_plans", Integer.class);
		int versionsBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plan_versions", Integer.class);
		int itemsBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plan_items", Integer.class);
		int cyclesBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plan_version_delivery_cycles", Integer.class);

		DemoCatalogManifestImportService.ImportResult result = importService.validate();

		assertThat(result.operation()).isEqualTo(DemoCatalogManifestImportService.Operation.VALIDATE);
		assertThat(result.categoriesCreated()).isEqualTo(4);
		assertThat(result.productsCreated()).isEqualTo(LocalCommerceDemoFixtureService.DEMO_PRODUCT_COUNT);
		assertThat(result.skusCreated()).isEqualTo(42);
		assertThat(result.inventoriesCreated()).isEqualTo(42);
		assertThat(result.plansCreated()).isEqualTo(6);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM categories", Integer.class)).isEqualTo(categoriesBefore);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Integer.class)).isEqualTo(productsBefore);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM skus", Integer.class)).isEqualTo(skusBefore);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventories", Integer.class)).isEqualTo(inventoriesBefore);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM subscription_plans", Integer.class)).isEqualTo(plansBefore);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plan_versions", Integer.class)).isEqualTo(versionsBefore);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plan_items", Integer.class)).isEqualTo(itemsBefore);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plan_version_delivery_cycles", Integer.class)).isEqualTo(cyclesBefore);
	}

	@Test
	void firstAndRepeatedRunCreateExactDatasetWithoutDuplicates() {
		fixtureService.bootstrap();
		fixtureService.bootstrap();

		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM categories WHERE slug IN ('food','treats','hygiene','toilet')", Integer.class))
				.isEqualTo(4);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM products WHERE catalog_key LIKE 'demo-%'", Integer.class))
				.isEqualTo(LocalCommerceDemoFixtureService.DEMO_PRODUCT_COUNT);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM skus WHERE sku_code LIKE 'DEMO-%'", Integer.class)).isEqualTo(42);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM subscription_plans WHERE plan_key LIKE 'demo-%'", Integer.class)).isEqualTo(6);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM plan_versions version JOIN subscription_plans plan ON plan.id=version.plan_id WHERE plan.plan_key LIKE 'demo-%'",
				Integer.class)).isEqualTo(6);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM plan_version_delivery_cycles cycle JOIN plan_versions version ON version.id=cycle.plan_version_id JOIN subscription_plans plan ON plan.id=version.plan_id WHERE plan.plan_key LIKE 'demo-%'",
				Integer.class)).isEqualTo(18);
	}

	@Test
	void datasetHasBalancedPetCategoryAndCommerceStateCoverage() {
		fixtureService.bootstrap();

		Map<String, Integer> categoryCounts = new java.util.HashMap<>();
		for (Map<String, Object> row : jdbcTemplate.queryForList(
				"SELECT pet_type,category.slug category_slug,COUNT(*) count FROM products product JOIN categories category ON category.id=product.category_id WHERE product.catalog_key LIKE 'demo-%' GROUP BY pet_type,category.slug")) {
			categoryCounts.put(row.get("pet_type") + "/" + row.get("category_slug"), ((Number) row.get("count")).intValue());
		}
		assertThat(categoryCounts).containsExactlyInAnyOrderEntriesOf(Map.of(
				"DOG/food", 5, "DOG/treats", 4, "DOG/hygiene", 3, "DOG/toilet", 4,
				"CAT/food", 5, "CAT/treats", 4, "CAT/hygiene", 2, "CAT/toilet", 5));
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM (SELECT product_id FROM skus WHERE sku_code LIKE 'DEMO-%' GROUP BY product_id HAVING COUNT(*) = 1) single_sku", Integer.class)).isGreaterThan(0);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM (SELECT product_id FROM skus WHERE sku_code LIKE 'DEMO-%' GROUP BY product_id HAVING COUNT(*) > 1) multi_sku", Integer.class)).isGreaterThan(0);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) - COUNT(DISTINCT sku_code) FROM skus WHERE sku_code LIKE 'DEMO-%'", Integer.class)).isZero();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM skus WHERE sku_code LIKE 'DEMO-%' AND subscribable=true", Integer.class)).isGreaterThan(0);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM skus WHERE sku_code LIKE 'DEMO-%' AND subscribable=false", Integer.class)).isGreaterThan(0);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM inventories inventory JOIN skus sku ON sku.id=inventory.sku_id WHERE sku.sku_code LIKE 'DEMO-%' AND inventory.available_quantity BETWEEN 1 AND 5", Integer.class)).isGreaterThan(0);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM products product WHERE product.catalog_key LIKE 'demo-%' AND EXISTS (SELECT 1 FROM skus sku JOIN inventories inventory ON inventory.sku_id=sku.id WHERE sku.product_id=product.id AND inventory.available_quantity=0) AND EXISTS (SELECT 1 FROM skus sku JOIN inventories inventory ON inventory.sku_id=sku.id WHERE sku.product_id=product.id AND inventory.available_quantity>0)", Integer.class)).isGreaterThan(0);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM products product WHERE product.catalog_key LIKE 'demo-%' AND NOT EXISTS (SELECT 1 FROM skus sku JOIN inventories inventory ON inventory.sku_id=sku.id WHERE sku.product_id=product.id AND inventory.available_quantity>0)", Integer.class)).isBetween(2, 3);
	}

	@Test
	void mismatchedExistingProductFailsWithoutOverwritingOrDuplicating() {
		fixtureService.bootstrap();
		jdbcTemplate.update("UPDATE products SET short_description=? WHERE name=?", "충돌 데이터", "데일리 밸런스 연어 사료");

		assertThatThrownBy(fixtureService::bootstrap)
				.isInstanceOf(LocalQaBootstrapException.class)
				.hasMessageContaining("product 데일리 밸런스 연어 사료");
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM products WHERE name=?", Integer.class, "데일리 밸런스 연어 사료")).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT short_description FROM products WHERE name=?", String.class, "데일리 밸런스 연어 사료")).isEqualTo("충돌 데이터");
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void conflictRollsBackRowsCreatedBeforeTheConflictThroughLocalWrapper() {
		try {
			jdbcTemplate.update(
					"INSERT INTO categories(name,slug,display_order,active) VALUES (?,?,?,false)",
					"충돌 카테고리", "treats", 999);

			assertThatThrownBy(fixtureService::bootstrap)
					.isInstanceOf(LocalQaBootstrapException.class)
					.hasMessageContaining("category treats");
			assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM categories WHERE slug='food'", Integer.class)).isZero();
			assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Integer.class)).isZero();
		} finally {
			jdbcTemplate.update("DELETE FROM categories WHERE slug IN ('food','treats','hygiene','toilet')");
		}
	}

	@Test
	void repeatedRunPreservesMutableInventoryState() {
		fixtureService.bootstrap();
		jdbcTemplate.update("""
				UPDATE inventories inventory
				JOIN skus sku ON sku.id=inventory.sku_id
				SET inventory.available_quantity=47, inventory.version=3
				WHERE sku.sku_code='DEMO-DOG-FOOD-SALMON-2KG'
				""");

		fixtureService.bootstrap();

		assertThat(jdbcTemplate.queryForObject(
				"SELECT inventory.available_quantity FROM inventories inventory JOIN skus sku ON sku.id=inventory.sku_id WHERE sku.sku_code=?",
				Integer.class, "DEMO-DOG-FOOD-SALMON-2KG")).isEqualTo(47);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT inventory.version FROM inventories inventory JOIN skus sku ON sku.id=inventory.sku_id WHERE sku.sku_code=?",
				Long.class, "DEMO-DOG-FOOD-SALMON-2KG")).isEqualTo(3L);
	}

	@Test
	void invalidManifestReferenceFailsClosed() {
		ReflectionTestUtils.setField(fixtureService, "manifestLocation", "classpath:catalog/missing-demo-catalog.json");

		assertThatThrownBy(fixtureService::bootstrap)
				.isInstanceOf(LocalQaBootstrapException.class)
				.hasMessageContaining("manifest");
	}
}
