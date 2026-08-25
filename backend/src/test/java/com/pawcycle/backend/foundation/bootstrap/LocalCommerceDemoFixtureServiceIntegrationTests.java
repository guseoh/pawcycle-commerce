package com.pawcycle.backend.foundation.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.pawcycle.backend.catalog.product.application.ProductListCacheInvalidator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LocalCommerceDemoFixtureServiceIntegrationTests {

	private final JdbcTemplate jdbcTemplate;
	private final LocalCommerceDemoFixtureService fixtureService;

	@Autowired
	LocalCommerceDemoFixtureServiceIntegrationTests(
			JdbcTemplate jdbcTemplate,
			ProductListCacheInvalidator productListCacheInvalidator) {
		this.jdbcTemplate = jdbcTemplate;
		this.fixtureService = new LocalCommerceDemoFixtureService(jdbcTemplate, productListCacheInvalidator);
	}

	@Test
	void firstAndRepeatedRunCreateExactDatasetWithoutDuplicates() {
		fixtureService.bootstrap();
		fixtureService.bootstrap();

		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM categories WHERE slug IN ('food','treats','hygiene','toilet')", Integer.class))
				.isEqualTo(4);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM products WHERE name IN (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Integer.class,
				"데일리 밸런스 연어 사료", "그레인프리 치킨 사료", "소프트 오리 트릿", "데일리 덴탈츄",
				"산책 후 풋 클렌저", "데일리 배변패드", "인도어 참치 사료", "헤어볼 케어 연어 사료",
				"참치 파우치 12팩", "동결건조 닭가슴살 트릿", "저자극 두부 모래", "무향 벤토나이트 모래"))
				.isEqualTo(LocalCommerceDemoFixtureService.DEMO_PRODUCT_COUNT);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM skus WHERE sku_code LIKE 'DEMO-%'", Integer.class)).isEqualTo(12);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM inventories inventory JOIN skus sku ON sku.id=inventory.sku_id WHERE sku.sku_code LIKE 'DEMO-%' AND inventory.available_quantity>0",
				Integer.class)).isEqualTo(12);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM subscription_plans WHERE name IN ('데일리 밸런스 플랜','데일리 케어 플랜','인도어 캣 플랜')",
				Integer.class)).isEqualTo(3);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM plan_versions version JOIN subscription_plans plan ON plan.id=version.plan_id WHERE plan.name IN ('데일리 밸런스 플랜','데일리 케어 플랜','인도어 캣 플랜')",
				Integer.class)).isEqualTo(3);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM plan_version_delivery_cycles cycle JOIN plan_versions version ON version.id=cycle.plan_version_id JOIN subscription_plans plan ON plan.id=version.plan_id WHERE plan.name IN ('데일리 밸런스 플랜','데일리 케어 플랜','인도어 캣 플랜')",
				Integer.class)).isEqualTo(9);
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
}
