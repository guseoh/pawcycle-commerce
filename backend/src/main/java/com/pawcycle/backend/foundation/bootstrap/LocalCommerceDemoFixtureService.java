package com.pawcycle.backend.foundation.bootstrap;

import com.pawcycle.backend.catalog.product.application.ProductListCacheInvalidator;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local-integration & !test & !production & !prod")
public class LocalCommerceDemoFixtureService {

	static final int DEMO_PRODUCT_COUNT = 12;
	static final List<Integer> DELIVERY_CYCLES = List.of(2, 4, 8);

	private static final List<CategoryFixture> CATEGORIES = List.of(
			new CategoryFixture("사료", "food", 1),
			new CategoryFixture("간식", "treats", 2),
			new CategoryFixture("위생", "hygiene", 3),
			new CategoryFixture("배변·모래", "toilet", 4));

	private static final List<ProductFixture> PRODUCTS = List.of(
			product("데일리 밸런스 연어 사료", "food", "매일 먹기 좋은 연어 기반 강아지 사료", "DOG", "2kg", "19900", true, "DEMO-DOG-FOOD-SALMON-2KG", 50),
			product("그레인프리 치킨 사료", "food", "담백한 치킨을 사용한 그레인프리 강아지 사료", "DOG", "2kg", "22900", true, "DEMO-DOG-FOOD-CHICKEN-2KG", 40),
			product("소프트 오리 트릿", "treats", "부드럽게 급여할 수 있는 오리 간식", "DOG", "200g", "8900", false, "DEMO-DOG-TREATS-DUCK-200G", 60),
			product("데일리 덴탈츄", "hygiene", "매일 간편하게 급여하는 덴탈 케어 츄", "DOG", "14개입", "12900", true, "DEMO-DOG-HYGIENE-DENTAL-14PCS", 45),
			product("산책 후 풋 클렌저", "hygiene", "산책 후 발을 부드럽게 씻는 강아지용 클렌저", "DOG", "300ml", "10900", false, "DEMO-DOG-HYGIENE-FOOT-CLEANER-300ML", 35),
			product("데일리 배변패드", "toilet", "매일 사용하는 흡수형 강아지 배변패드", "DOG", "50매", "14900", true, "DEMO-DOG-TOILET-PAD-50PCS", 70),
			product("인도어 참치 사료", "food", "실내 생활 고양이를 위한 참치 기반 사료", "CAT", "1.5kg", "18900", true, "DEMO-CAT-FOOD-TUNA-1-5KG", 50),
			product("헤어볼 케어 연어 사료", "food", "헤어볼 케어를 고려한 연어 기반 고양이 사료", "CAT", "1.5kg", "21900", true, "DEMO-CAT-FOOD-SALMON-HAIRBALL-1-5KG", 40),
			product("참치 파우치 12팩", "food", "한 끼씩 간편하게 급여하는 참치 파우치", "CAT", "12팩", "15900", true, "DEMO-CAT-FOOD-TUNA-POUCH-12PACK", 55),
			product("동결건조 닭가슴살 트릿", "treats", "바삭하게 즐기는 닭가슴살 동결건조 간식", "CAT", "100g", "9900", false, "DEMO-CAT-TREATS-CHICKEN-FD-100G", 60),
			product("저자극 두부 모래", "toilet", "가볍고 관리하기 편한 고양이 두부 모래", "CAT", "7L", "13900", true, "DEMO-CAT-TOILET-TOFU-7L", 65),
			product("무향 벤토나이트 모래", "toilet", "향을 더하지 않은 고양이용 벤토나이트 모래", "CAT", "6L", "11900", true, "DEMO-CAT-TOILET-BENTONITE-6L", 65));

	private static final List<PlanFixture> PLANS = List.of(
			new PlanFixture("데일리 밸런스 플랜", "DOG", 19_900, List.of(new PlanItemFixture("DEMO-DOG-FOOD-SALMON-2KG", 1))),
			new PlanFixture("데일리 케어 플랜", "DOG", 29_900, List.of(
					new PlanItemFixture("DEMO-DOG-FOOD-SALMON-2KG", 1),
					new PlanItemFixture("DEMO-DOG-HYGIENE-DENTAL-14PCS", 1))),
			new PlanFixture("인도어 캣 플랜", "CAT", 29_900, List.of(
					new PlanItemFixture("DEMO-CAT-FOOD-TUNA-1-5KG", 1),
					new PlanItemFixture("DEMO-CAT-TOILET-TOFU-7L", 1))));

	private final JdbcTemplate jdbcTemplate;
	private final ProductListCacheInvalidator productListCacheInvalidator;

	public LocalCommerceDemoFixtureService(
			JdbcTemplate jdbcTemplate,
			ProductListCacheInvalidator productListCacheInvalidator) {
		this.jdbcTemplate = jdbcTemplate;
		this.productListCacheInvalidator = productListCacheInvalidator;
	}

	@Transactional
	public void bootstrap() {
		Map<String, Long> categoryIds = new HashMap<>();
		for (CategoryFixture fixture : CATEGORIES) {
			categoryIds.put(fixture.slug(), ensureCategory(fixture));
		}

		Map<String, Long> skuIds = new LinkedHashMap<>();
		for (ProductFixture fixture : PRODUCTS) {
			long productId = ensureProduct(fixture, categoryIds.get(fixture.categorySlug()));
			long skuId = ensureSku(fixture, productId);
			ensureInventory(fixture, skuId);
			skuIds.put(fixture.skuCode(), skuId);
		}

		for (PlanFixture fixture : PLANS) {
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
		if (rows.size() != 1 || !matchesCategory(rows.getFirst(), fixture)) {
			throw fixtureConflict("category " + fixture.slug());
		}
		return number(rows.getFirst(), "id");
	}

	private long ensureProduct(ProductFixture fixture, long categoryId) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(
				"""
				SELECT id,category_id,name,short_description,description,pet_type,thumbnail_url,display_status
				FROM products
				WHERE name=?
				FOR UPDATE
				""",
				fixture.name());
		if (rows.isEmpty()) {
			jdbcTemplate.update(
					"""
					INSERT INTO products(category_id,name,short_description,description,pet_type,thumbnail_url,display_status)
					VALUES (?,?,?,?,?,NULL,'PUBLIC')
					""",
					categoryId, fixture.name(), fixture.description(), fixture.description(), fixture.petType());
			return lastInsertedId();
		}
		if (rows.size() != 1 || !matchesProduct(rows.getFirst(), fixture, categoryId)) {
			throw fixtureConflict("product " + fixture.name());
		}
		return number(rows.getFirst(), "id");
	}

	private long ensureSku(ProductFixture fixture, long productId) {
		List<Map<String, Object>> codeRows = jdbcTemplate.queryForList(
				"SELECT id,product_id,sku_code,name,price,subscribable,display_order,status FROM skus WHERE sku_code=? FOR UPDATE",
				fixture.skuCode());
		List<Map<String, Object>> nameRows = jdbcTemplate.queryForList(
				"SELECT id,sku_code FROM skus WHERE product_id=? AND name=? FOR UPDATE",
				productId, fixture.skuName());
		if (codeRows.isEmpty() && nameRows.isEmpty()) {
			jdbcTemplate.update(
					"""
					INSERT INTO skus(product_id,sku_code,name,price,subscribable,display_order,status)
					VALUES (?,?,?,?,?,1,'ACTIVE')
					""",
					productId, fixture.skuCode(), fixture.skuName(), fixture.price(), fixture.subscribable());
			return lastInsertedId();
		}
		if (codeRows.size() != 1 || nameRows.size() != 1
				|| !matchesSku(codeRows.getFirst(), fixture, productId)) {
			throw fixtureConflict("SKU " + fixture.skuCode());
		}
		return number(codeRows.getFirst(), "id");
	}

	private void ensureInventory(ProductFixture fixture, long skuId) {
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
				SELECT id,target_pet_type,on_sale,sale_starts_on,sale_ends_on,current_plan_version_id
				FROM subscription_plans
				WHERE name=?
				FOR UPDATE
				""",
				fixture.name());
		if (rows.isEmpty()) {
				createPlan(fixture, skuIds);
				return;
		}
		if (rows.size() != 1) {
			throw fixtureConflict("plan " + fixture.name());
		}
		validatePlan(rows.getFirst(), fixture, skuIds);
	}

	private void createPlan(PlanFixture fixture, Map<String, Long> skuIds) {
		jdbcTemplate.update(
				"INSERT INTO subscription_plans(name,target_pet_type,on_sale,sale_starts_on,sale_ends_on,current_plan_version_id) VALUES (?,?,true,NULL,NULL,NULL)",
				fixture.name(), fixture.petType());
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
		return number(row, "category_id") == categoryId
				&& fixture.name().equals(row.get("name"))
				&& fixture.description().equals(row.get("short_description"))
				&& fixture.description().equals(row.get("description"))
				&& fixture.petType().equals(row.get("pet_type"))
				&& row.get("thumbnail_url") == null
				&& "PUBLIC".equals(row.get("display_status"));
	}

	private boolean matchesSku(Map<String, Object> row, ProductFixture fixture, long productId) {
		return number(row, "product_id") == productId
				&& fixture.skuCode().equals(row.get("sku_code"))
				&& fixture.skuName().equals(row.get("name"))
				&& fixture.price().compareTo(new BigDecimal(row.get("price").toString())) == 0
				&& fixture.subscribable() == trueValue(row.get("subscribable"))
				&& number(row, "display_order") == 1
				&& "ACTIVE".equals(row.get("status"));
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

	private static ProductFixture product(
			String name,
			String categorySlug,
			String description,
			String petType,
			String skuName,
			String price,
			boolean subscribable,
			String skuCode,
			int initialInventory) {
		return new ProductFixture(name, categorySlug, description, petType, skuName,
				new BigDecimal(price), subscribable, skuCode, initialInventory);
	}

	private record CategoryFixture(String name, String slug, int displayOrder) {}

	private record ProductFixture(
			String name,
			String categorySlug,
			String description,
			String petType,
			String skuName,
			BigDecimal price,
			boolean subscribable,
			String skuCode,
			int initialInventory) {}

	private record PlanFixture(String name, String petType, long packagePriceKrw, List<PlanItemFixture> items) {}

	private record PlanItemFixture(String skuCode, int quantity) {}
}
