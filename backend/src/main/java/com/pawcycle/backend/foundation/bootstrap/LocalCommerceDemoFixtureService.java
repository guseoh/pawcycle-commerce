package com.pawcycle.backend.foundation.bootstrap;

import com.pawcycle.backend.catalog.product.application.ProductListCacheInvalidator;
import java.math.BigDecimal;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.ObjectMapper;

@Service
@Profile("local-integration & !test & !production & !prod")
public class LocalCommerceDemoFixtureService {

	static final int DEMO_PRODUCT_COUNT = 32;
	static final List<Integer> DELIVERY_CYCLES = List.of(2, 4, 8);

	private final JdbcTemplate jdbcTemplate;
	private final ProductListCacheInvalidator productListCacheInvalidator;
	private final ObjectMapper objectMapper;
	@Value("${pawcycle.local-demo-catalog.manifest:classpath:catalog/demo-catalog.json}")
	private String manifestLocation;

	public LocalCommerceDemoFixtureService(
			JdbcTemplate jdbcTemplate,
			ProductListCacheInvalidator productListCacheInvalidator) {
		this.jdbcTemplate = jdbcTemplate;
		this.productListCacheInvalidator = productListCacheInvalidator;
		this.objectMapper = new ObjectMapper();
	}

	@Transactional
	public void bootstrap() {
		CatalogManifest manifest = loadManifest();
		Map<String, Long> categoryIds = new HashMap<>();
		for (CategoryFixture fixture : manifest.categories()) {
			categoryIds.put(fixture.slug(), ensureCategory(fixture));
		}

		Map<String, Long> skuIds = new LinkedHashMap<>();
		for (ProductFixture fixture : manifest.products()) {
			long productId = ensureProduct(fixture, categoryIds.get(fixture.categorySlug()));
			for (SkuFixture sku : fixture.skus()) {
				long skuId = ensureSku(sku, productId);
				ensureInventory(sku, skuId);
				skuIds.put(sku.skuCode(), skuId);
			}
		}

		for (PlanFixture fixture : manifest.plans()) {
			ensurePlan(fixture, skuIds);
		}
		productListCacheInvalidator.invalidateAfterCommit();
	}

	private long ensureCategory(CategoryFixture fixture) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(
				"SELECT id,name,slug,display_order,active FROM categories WHERE slug=? FOR UPDATE", fixture.slug());
		if (rows.isEmpty()) {
			jdbcTemplate.update(
					"INSERT INTO categories(name,slug,display_order,active) VALUES (?,?,?,true)",
					fixture.name(), fixture.slug(), fixture.displayOrder());
			return lastInsertedId();
		}
		if (rows.size() != 1) {
			throw fixtureConflict("category " + fixture.slug());
		}
		if (!matchesCategory(rows.getFirst(), fixture)) {
			if (number(rows.getFirst(), "display_order") == fixture.displayOrder() && trueValue(rows.getFirst().get("active"))) {
				jdbcTemplate.update("UPDATE categories SET name=? WHERE id=?", fixture.name(), number(rows.getFirst(), "id"));
			} else throw fixtureConflict("category " + fixture.slug());
		}
		return number(rows.getFirst(), "id");
	}

	private long ensureProduct(ProductFixture fixture, long categoryId) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(
				"""
				SELECT id,catalog_key,category_id,name,short_description,description,pet_type,thumbnail_url,display_status
				FROM products
				WHERE catalog_key=?
				FOR UPDATE
				""",
				fixture.catalogKey());
		if (rows.isEmpty()) {
			List<Map<String, Object>> nameRows = jdbcTemplate.queryForList(
					"SELECT id,catalog_key,category_id,name,short_description,description,pet_type,thumbnail_url,display_status FROM products WHERE name=? FOR UPDATE", fixture.name());
			if (nameRows.size() == 1 && String.valueOf(nameRows.getFirst().get("catalog_key")).startsWith("legacy-product-")
					&& matchesProductFields(nameRows.getFirst(), fixture, categoryId, true, false)) {
				jdbcTemplate.update("UPDATE products SET catalog_key=?,thumbnail_url=? WHERE id=?",
						fixture.catalogKey(), fixture.thumbnailUrl(), number(nameRows.getFirst(), "id"));
				return number(nameRows.getFirst(), "id");
			}
			if (!nameRows.isEmpty()) throw fixtureConflict("product " + fixture.catalogKey());
			jdbcTemplate.update(
					"""
					INSERT INTO products(catalog_key,category_id,name,short_description,description,pet_type,thumbnail_url,display_status)
					VALUES (?,?,?,?,?,?,?,'PUBLIC')
					""",
					fixture.catalogKey(), categoryId, fixture.name(), fixture.shortDescription(), fixture.description(), fixture.petType(), fixture.thumbnailUrl());
			return lastInsertedId();
		}
		if (rows.size() != 1) {
			throw fixtureConflict("product " + fixture.name());
		}
		if (!matchesProduct(rows.getFirst(), fixture, categoryId)) {
			if (fixture.catalogKey().equals(rows.getFirst().get("catalog_key"))
					&& matchesProductFields(rows.getFirst(), fixture, categoryId, false, true)) {
				jdbcTemplate.update("UPDATE products SET name=? WHERE id=?", fixture.name(), number(rows.getFirst(), "id"));
			} else throw fixtureConflict("product " + fixture.name());
		}
		return number(rows.getFirst(), "id");
	}

	private long ensureSku(SkuFixture fixture, long productId) {
		List<Map<String, Object>> codeRows = jdbcTemplate.queryForList(
				"SELECT id,product_id,sku_code,name,price,subscribable,display_order,status FROM skus WHERE sku_code=? FOR UPDATE",
				fixture.skuCode());
		List<Map<String, Object>> nameRows = jdbcTemplate.queryForList(
				"SELECT id,sku_code FROM skus WHERE product_id=? AND sku_code=? FOR UPDATE",
				productId, fixture.skuCode());
		if (codeRows.isEmpty() && nameRows.isEmpty()) {
			jdbcTemplate.update(
					"""
					INSERT INTO skus(product_id,sku_code,name,price,subscribable,display_order,status)
					VALUES (?,?,?,?,?,?,?)
					""",
					productId, fixture.skuCode(), fixture.name(), fixture.price(), fixture.subscribable(), fixture.displayOrder(), fixture.status());
			return lastInsertedId();
		}
		if (codeRows.size() != 1 || nameRows.size() != 1 || !matchesSku(codeRows.getFirst(), fixture, productId, true)) {
			throw fixtureConflict("SKU " + fixture.skuCode());
		}
		if (!fixture.name().equals(codeRows.getFirst().get("name"))) {
			jdbcTemplate.update("UPDATE skus SET name=? WHERE id=?", fixture.name(), number(codeRows.getFirst(), "id"));
		}
		return number(codeRows.getFirst(), "id");
	}

	private void ensureInventory(SkuFixture fixture, long skuId) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(
				"SELECT sku_id,available_quantity,reserved_quantity,version FROM inventories WHERE sku_id=? FOR UPDATE", skuId);
		if (rows.isEmpty()) {
			jdbcTemplate.update(
					"INSERT INTO inventories(sku_id,available_quantity,reserved_quantity,version) VALUES (?,?,0,0)",
					skuId, fixture.initialInventory());
			return;
		}
		if (rows.size() != 1 || number(rows.getFirst(), "sku_id") != skuId
				|| number(rows.getFirst(), "available_quantity") < 0
				|| number(rows.getFirst(), "reserved_quantity") < 0
				|| number(rows.getFirst(), "version") < 0) {
			throw fixtureConflict("inventory " + fixture.skuCode());
		}
		// Inventory is mutable commerce state. A restart must not reset stock consumed by a demo checkout.
	}

	private void ensurePlan(PlanFixture fixture, Map<String, Long> skuIds) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(
				"""
				SELECT id,plan_key,name,target_pet_type,on_sale,sale_starts_on,sale_ends_on,current_plan_version_id
				FROM subscription_plans
				WHERE plan_key=?
				FOR UPDATE
				""",
				fixture.planKey());
		if (rows.isEmpty()) {
			List<Map<String, Object>> nameRows = jdbcTemplate.queryForList(
					"SELECT id,plan_key,name,target_pet_type,on_sale,sale_starts_on,sale_ends_on,current_plan_version_id FROM subscription_plans WHERE name=? FOR UPDATE", fixture.name());
			if (nameRows.size() == 1 && String.valueOf(nameRows.getFirst().get("plan_key")).startsWith("legacy-plan-")) {
				jdbcTemplate.update("UPDATE subscription_plans SET plan_key=? WHERE id=?", fixture.planKey(), number(nameRows.getFirst(), "id"));
				rows = nameRows;
			}
			if (!nameRows.isEmpty() && rows.isEmpty()) throw fixtureConflict("plan " + fixture.planKey());
			if (rows.isEmpty()) {
				createPlan(fixture, skuIds);
				return;
			}
		}
		if (rows.size() != 1) {
			throw fixtureConflict("plan " + fixture.name());
		}
		if (!fixture.name().equals(rows.getFirst().get("name"))) {
			jdbcTemplate.update("UPDATE subscription_plans SET name=? WHERE id=?", fixture.name(), number(rows.getFirst(), "id"));
		}
		validatePlan(rows.getFirst(), fixture, skuIds);
	}

	private void createPlan(PlanFixture fixture, Map<String, Long> skuIds) {
		jdbcTemplate.update(
			"INSERT INTO subscription_plans(plan_key,name,target_pet_type,on_sale,sale_starts_on,sale_ends_on,current_plan_version_id) VALUES (?,?,?,true,NULL,NULL,NULL)",
				fixture.planKey(), fixture.name(), fixture.petType());
		long planId = lastInsertedId();
		jdbcTemplate.update(
				"INSERT INTO plan_versions(plan_id,package_price_krw,is_migration_only) VALUES (?,?,false)",
				planId, fixture.packagePriceKrw());
		long planVersionId = lastInsertedId();
		for (PlanItemFixture item : fixture.items()) {
			Long skuId = skuIds.get(item.skuCode());
			if (skuId == null) throw fixtureConflict("plan item " + item.skuCode());
			jdbcTemplate.update("INSERT INTO plan_items(plan_version_id,sku_id,quantity) VALUES (?,?,?)",
					planVersionId, skuId, item.quantity());
		}
		for (Integer cycle : DELIVERY_CYCLES) {
			jdbcTemplate.update(
					"INSERT INTO plan_version_delivery_cycles(plan_version_id,delivery_cycle_weeks) VALUES (?,?)",
					planVersionId, cycle);
		}
		jdbcTemplate.update("UPDATE subscription_plans SET current_plan_version_id=? WHERE id=?", planVersionId, planId);
	}

	private void validatePlan(Map<String, Object> row, PlanFixture fixture, Map<String, Long> skuIds) {
		if (!fixture.petType().equals(row.get("target_pet_type"))
				|| !trueValue(row.get("on_sale"))
				|| row.get("sale_starts_on") != null
				|| row.get("sale_ends_on") != null
				|| !(row.get("current_plan_version_id") instanceof Number currentVersion)) {
			throw fixtureConflict("plan " + fixture.name());
		}
		long planId = number(row, "id");
		long planVersionId = currentVersion.longValue();
		Integer versionCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM plan_versions WHERE plan_id=?", Integer.class, planId);
		List<Map<String, Object>> versions = jdbcTemplate.queryForList(
				"SELECT id,plan_id,package_price_krw,is_migration_only FROM plan_versions WHERE id=? AND plan_id=?",
				planVersionId, planId);
		Integer itemCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM plan_items WHERE plan_version_id=?", Integer.class, planVersionId);
		Integer cycleCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM plan_version_delivery_cycles WHERE plan_version_id=?", Integer.class, planVersionId);
		Map<Long, Integer> actualItems = new HashMap<>();
		for (Map<String, Object> item : jdbcTemplate.queryForList(
				"SELECT sku_id,quantity FROM plan_items WHERE plan_version_id=?", planVersionId)) {
			actualItems.put(number(item, "sku_id"), Math.toIntExact(number(item, "quantity")));
		}
		Map<Long, Integer> expectedItems = new HashMap<>();
		for (PlanItemFixture item : fixture.items()) {
			Long skuId = skuIds.get(item.skuCode());
			if (skuId == null) throw fixtureConflict("plan item " + item.skuCode());
			expectedItems.put(skuId, item.quantity());
		}
		List<Integer> cycles = jdbcTemplate.queryForList(
				"SELECT delivery_cycle_weeks FROM plan_version_delivery_cycles WHERE plan_version_id=? ORDER BY delivery_cycle_weeks",
				Integer.class, planVersionId);
		if (versionCount == null || versionCount != 1
				|| versions.size() != 1
				|| !matchesPlanVersion(versions.getFirst(), planVersionId, planId, fixture.packagePriceKrw())
				|| itemCount == null || itemCount != expectedItems.size()
				|| !expectedItems.equals(actualItems)
				|| cycleCount == null || cycleCount != DELIVERY_CYCLES.size()
				|| !DELIVERY_CYCLES.equals(cycles)) {
			throw fixtureConflict("plan " + fixture.name());
		}
	}

	private boolean matchesCategory(Map<String, Object> row, CategoryFixture fixture) {
		return fixture.name().equals(row.get("name"))
				&& fixture.slug().equals(row.get("slug"))
				&& number(row, "display_order") == fixture.displayOrder()
				&& trueValue(row.get("active"));
	}

	private boolean matchesProduct(Map<String, Object> row, ProductFixture fixture, long categoryId) {
		return fixture.catalogKey().equals(row.get("catalog_key")) && matchesProductFields(row, fixture, categoryId, false, false);
	}

	private boolean matchesProductFields(Map<String, Object> row, ProductFixture fixture, long categoryId, boolean allowLegacyImage, boolean allowNameChange) {
		return number(row, "category_id") == categoryId
				&& (allowNameChange || fixture.name().equals(row.get("name")))
				&& fixture.shortDescription().equals(row.get("short_description"))
				&& fixture.description().equals(row.get("description"))
				&& fixture.petType().equals(row.get("pet_type"))
				&& (java.util.Objects.equals(fixture.thumbnailUrl(), row.get("thumbnail_url"))
						|| (allowLegacyImage && row.get("thumbnail_url") == null))
				&& "PUBLIC".equals(row.get("display_status"));
	}

	private boolean matchesSku(Map<String, Object> row, SkuFixture fixture, long productId, boolean allowNameChange) {
		return number(row, "product_id") == productId
				&& fixture.skuCode().equals(row.get("sku_code"))
				&& (allowNameChange || fixture.name().equals(row.get("name")))
				&& fixture.price().compareTo(new BigDecimal(row.get("price").toString())) == 0
				&& fixture.subscribable() == trueValue(row.get("subscribable"))
				&& number(row, "display_order") == fixture.displayOrder()
				&& fixture.status().equals(row.get("status"));
	}

	private boolean matchesPlanVersion(Map<String, Object> row, long versionId, long planId, long price) {
		return number(row, "id") == versionId
				&& number(row, "plan_id") == planId
				&& number(row, "package_price_krw") == price
				&& !trueValue(row.get("is_migration_only"));
	}

	private long lastInsertedId() {
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private LocalQaBootstrapException fixtureConflict(String fixture) {
		return new LocalQaBootstrapException("로컬 Commerce Demo fixture가 기존 데이터와 충돌합니다: " + fixture);
	}

	private static long number(Map<String, Object> row, String key) {
		return ((Number) row.get(key)).longValue();
	}

	private static boolean trueValue(Object value) {
		if (value instanceof Boolean booleanValue) return booleanValue;
		return value instanceof Number number && number.intValue() == 1;
	}

	private CatalogManifest loadManifest() {
		try {
			String location = manifestLocation == null || manifestLocation.isBlank()
					? "classpath:catalog/demo-catalog.json" : manifestLocation;
			Resource resource = new DefaultResourceLoader().getResource(location);
			CatalogManifest manifest = objectMapper.readValue(resource.getInputStream(), CatalogManifest.class);
			if (manifest.version() != 1 || manifest.categories() == null || manifest.products() == null || manifest.plans() == null) {
				throw new LocalQaBootstrapException("로컬 Commerce Demo manifest 버전 또는 필수 목록이 올바르지 않습니다.");
			}
			Set<String> categorySlugs = new HashSet<>();
			for (CategoryFixture category : manifest.categories()) {
				if (category == null || category.slug() == null || !categorySlugs.add(category.slug())) {
					throw new LocalQaBootstrapException("로컬 Commerce Demo manifest의 Category slug가 중복/누락되었습니다.");
				}
			}
			Set<String> catalogKeys = new HashSet<>();
			Set<String> skuCodes = new HashSet<>();
			for (ProductFixture product : manifest.products()) {
				if (product == null || product.catalogKey() == null || !catalogKeys.add(product.catalogKey())
						|| product.categorySlug() == null || !categorySlugs.contains(product.categorySlug())
						|| product.skus() == null || product.skus().isEmpty()) {
					throw new LocalQaBootstrapException("로컬 Commerce Demo manifest의 Product business key 또는 SKU 목록이 중복/누락되었습니다.");
				}
				for (SkuFixture sku : product.skus()) {
					if (sku == null || sku.skuCode() == null || !skuCodes.add(sku.skuCode())) {
						throw new LocalQaBootstrapException("로컬 Commerce Demo manifest의 SKU business key가 중복/누락되었습니다.");
					}
				}
			}
			Set<String> planKeys = new HashSet<>();
			for (PlanFixture plan : manifest.plans()) {
				if (plan == null || plan.planKey() == null || !planKeys.add(plan.planKey()) || plan.items() == null || plan.items().isEmpty()) {
					throw new LocalQaBootstrapException("로컬 Commerce Demo manifest의 Plan business key 또는 item 목록이 중복/누락되었습니다.");
				}
				Set<String> planSkuCodes = new HashSet<>();
				for (PlanItemFixture item : plan.items()) {
					if (item == null || item.skuCode() == null || !skuCodes.contains(item.skuCode()) || !planSkuCodes.add(item.skuCode())) {
						throw new LocalQaBootstrapException("로컬 Commerce Demo manifest의 Plan item SKU가 중복/누락되었습니다.");
					}
				}
			}
			return manifest;
		} catch (IOException | RuntimeException exception) {
			if (exception instanceof LocalQaBootstrapException conflict) throw conflict;
			throw new LocalQaBootstrapException("로컬 Commerce Demo manifest를 읽을 수 없습니다.", exception);
		}
	}

	private record CatalogManifest(int version, List<CategoryFixture> categories, List<ProductFixture> products, List<PlanFixture> plans) {}
	private record CategoryFixture(String name, String slug, int displayOrder) {}

	private record ProductFixture(
			String catalogKey,
			String name,
			String categorySlug,
			String shortDescription,
			String description,
			String petType,
			String thumbnailUrl,
			List<SkuFixture> skus) {}

	private record SkuFixture(String skuCode, String name, BigDecimal price, boolean subscribable, int displayOrder, String status, int initialInventory) {}

	private record PlanFixture(String planKey, String name, String petType, long packagePriceKrw, List<PlanItemFixture> items) {}

	private record PlanItemFixture(String skuCode, int quantity) {}
}
