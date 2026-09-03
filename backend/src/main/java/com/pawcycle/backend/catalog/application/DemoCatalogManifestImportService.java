package com.pawcycle.backend.catalog.application;

import com.pawcycle.backend.catalog.product.application.ProductListCacheInvalidator;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class DemoCatalogManifestImportService {

  public static final String DEFAULT_MANIFEST_LOCATION = "classpath:catalog/demo-catalog.json";
  private static final String DEMO_BRAND_SLUG = "pawcycle-demo-catalog";
  static final int DELIVERY_CYCLE_COUNT = 3;
  static final List<Integer> DELIVERY_CYCLES = List.of(2, 4, 8);

  private final NativeQueryExecutor jdbcTemplate;
  private final ProductListCacheInvalidator productListCacheInvalidator;
  private final CustomerCatalogRealismCorrectionService correction;
  private final ObjectMapper objectMapper;
  private final String configuredManifestLocation;

  @Autowired
  public DemoCatalogManifestImportService(
      NativeQueryExecutor jdbcTemplate,
      ProductListCacheInvalidator productListCacheInvalidator,
      CustomerCatalogRealismCorrectionService correction,
      @Value("${pawcycle.catalog.demo.manifest:" + DEFAULT_MANIFEST_LOCATION + "}")
          String configuredManifestLocation) {
    this.jdbcTemplate = jdbcTemplate;
    this.productListCacheInvalidator = productListCacheInvalidator;
    this.correction = correction;
    this.objectMapper = new ObjectMapper();
    this.configuredManifestLocation = configuredManifestLocation;
  }

  public DemoCatalogManifestImportService(
      NativeQueryExecutor jdbcTemplate, ProductListCacheInvalidator productListCacheInvalidator) {
    this(
        jdbcTemplate,
        productListCacheInvalidator,
        new CustomerCatalogRealismCorrectionService(jdbcTemplate),
        DEFAULT_MANIFEST_LOCATION);
  }

  public DemoCatalogManifestImportService(
      NativeQueryExecutor jdbcTemplate,
      ProductListCacheInvalidator productListCacheInvalidator,
      String configuredManifestLocation) {
    this(
        jdbcTemplate,
        productListCacheInvalidator,
        new CustomerCatalogRealismCorrectionService(jdbcTemplate),
        configuredManifestLocation);
  }

  @Transactional
  public DemoCatalogImportResult validate() {
    return validate(configuredManifestLocation);
  }

  @Transactional
  public DemoCatalogImportResult validate(String manifestLocation) {
    CatalogManifest manifest = loadManifest(manifestLocation);
    ImportContext context = new ImportContext(DemoCatalogImportOperation.VALIDATE);
    process(manifest, context);
    return context.result(null);
  }

  @Transactional
  public DemoCatalogImportResult apply() {
    return apply(configuredManifestLocation);
  }

  @Transactional
  public DemoCatalogImportResult apply(String manifestLocation) {
    CatalogManifest manifest = loadManifest(manifestLocation);
    ImportContext context = new ImportContext(DemoCatalogImportOperation.APPLY);
    process(manifest, context);
    DemoCatalogImportPostflight postflight = postflight(manifest);
    if (!postflight.complete()) {
      throw conflict("manifest postflight");
    }
    productListCacheInvalidator.invalidateAfterCommit();
    return context.result(postflight);
  }

  private void process(CatalogManifest manifest, ImportContext context) {
    long demoBrandId = ensureDemoBrand(context);
    Map<String, Long> categoryIds = new HashMap<>();
    for (CategoryFixture fixture : manifest.categories()) {
      categoryIds.put(fixture.slug(), ensureCategory(fixture, context));
    }

    Map<String, Long> skuIds = new LinkedHashMap<>();
    for (ProductFixture fixture : manifest.products()) {
      Long categoryId = categoryIds.get(fixture.categorySlug());
      if (categoryId == null) {
        throw conflict("product category " + fixture.categorySlug());
      }
      long productId = ensureProduct(fixture, categoryId, demoBrandId, context);
      for (SkuFixture sku : fixture.skus()) {
        long skuId = ensureSku(sku, productId, context);
        ensureInventory(sku, skuId, context);
        skuIds.put(sku.skuCode(), skuId);
      }
    }

    for (PlanFixture fixture : manifest.plans()) {
      ensurePlan(fixture, skuIds, context);
    }
  }

  private long ensureDemoBrand(ImportContext context) {
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList("SELECT id FROM brands WHERE slug=? FOR UPDATE", DEMO_BRAND_SLUG);
    if (rows.size() == 1) return number(rows.getFirst(), "id");
    if (!rows.isEmpty()) throw conflict("demo brand");
    if (!context.writes()) return context.virtualId();
    jdbcTemplate.update(
        "INSERT INTO brands(name,slug,logo_url,active,display_order) VALUES (?,?,NULL,true,0)",
        "PawCycle Demo Catalog",
        DEMO_BRAND_SLUG);
    return lastInsertedId();
  }

  private long ensureCategory(CategoryFixture fixture, ImportContext context) {
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT id,name,slug,display_order,active FROM categories WHERE slug=? FOR UPDATE",
            fixture.slug());
    if (rows.isEmpty()) {
      context.createdCategories++;
      if (!context.writes()) return context.virtualId();
      jdbcTemplate.update(
          "INSERT INTO categories(name,slug,display_order,active) VALUES (?,?,?,true)",
          fixture.name(),
          fixture.slug(),
          fixture.displayOrder());
      return lastInsertedId();
    }
    if (rows.size() != 1) {
      throw conflict("category " + fixture.slug());
    }
    if (!matchesCategory(rows.getFirst(), fixture)) {
      if (number(rows.getFirst(), "display_order") == fixture.displayOrder()
          && trueValue(rows.getFirst().get("active"))) {
        if (context.writes()) {
          jdbcTemplate.update(
              "UPDATE categories SET name=? WHERE id=?",
              fixture.name(),
              number(rows.getFirst(), "id"));
        }
      } else {
        throw conflict("category " + fixture.slug());
      }
    }
    return number(rows.getFirst(), "id");
  }

  private long ensureProduct(
      ProductFixture fixture, long categoryId, long brandId, ImportContext context) {
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            """
            SELECT id,catalog_key,category_id,brand_id,name,short_description,description,pet_type,thumbnail_url,display_status
            FROM products
            WHERE catalog_key=?
            FOR UPDATE
            """,
            fixture.catalogKey());
    if (rows.isEmpty()) {
      List<Map<String, Object>> nameRows =
          jdbcTemplate.queryForList(
              "SELECT"
                  + " id,catalog_key,category_id,brand_id,name,short_description,description,pet_type,thumbnail_url,display_status"
                  + " FROM products WHERE name=? FOR UPDATE",
              fixture.name());
      if (nameRows.size() == 1
          && String.valueOf(nameRows.getFirst().get("catalog_key")).startsWith("legacy-product-")
          && matchesProductFields(nameRows.getFirst(), fixture, categoryId, brandId, true, false)) {
        if (context.writes()) {
          jdbcTemplate.update(
              "UPDATE products SET catalog_key=?,thumbnail_url=?,brand_id=? WHERE id=?",
              fixture.catalogKey(),
              fixture.thumbnailUrl(),
              brandId,
              number(nameRows.getFirst(), "id"));
        }
        return number(nameRows.getFirst(), "id");
      }
      if (!nameRows.isEmpty()) throw conflict("product " + fixture.catalogKey());
      context.createdProducts++;
      if (!context.writes()) return context.virtualId();
      jdbcTemplate.update(
          """
          INSERT INTO products(catalog_key,category_id,brand_id,name,short_description,description,pet_type,thumbnail_url,display_status)
          VALUES (?,?,?,?,?,?,?,?,'PUBLIC')
          """,
          fixture.catalogKey(),
          categoryId,
          brandId,
          fixture.name(),
          fixture.shortDescription(),
          fixture.description(),
          fixture.petType(),
          fixture.thumbnailUrl());
      return lastInsertedId();
    }
    if (rows.size() != 1) {
      throw conflict("product " + fixture.name());
    }
    if (!matchesProduct(rows.getFirst(), fixture, categoryId, brandId)) {
      if (fixture.catalogKey().equals(rows.getFirst().get("catalog_key"))
          && matchesProductFields(rows.getFirst(), fixture, categoryId, brandId, false, true)) {
        if (context.writes()) {
          jdbcTemplate.update(
              "UPDATE products SET name=? WHERE id=?",
              fixture.name(),
              number(rows.getFirst(), "id"));
        }
      } else {
        throw conflict("product " + fixture.name());
      }
    }
    return number(rows.getFirst(), "id");
  }

  private long ensureSku(SkuFixture fixture, long productId, ImportContext context) {
    List<Map<String, Object>> codeRows =
        jdbcTemplate.queryForList(
            "SELECT id,product_id,sku_code,name,price,subscribable,display_order,status FROM skus"
                + " WHERE sku_code=? FOR UPDATE",
            fixture.skuCode());
    List<Map<String, Object>> nameRows =
        jdbcTemplate.queryForList(
            "SELECT id,sku_code FROM skus WHERE product_id=? AND sku_code=? FOR UPDATE",
            productId,
            fixture.skuCode());
    if (codeRows.isEmpty() && nameRows.isEmpty()) {
      context.createdSkus++;
      if (!context.writes()) return context.virtualId();
      jdbcTemplate.update(
          """
          INSERT INTO skus(product_id,sku_code,name,price,subscribable,display_order,status)
          VALUES (?,?,?,?,?,?,?)
          """,
          productId,
          fixture.skuCode(),
          fixture.name(),
          fixture.price(),
          fixture.subscribable(),
          fixture.displayOrder(),
          fixture.status());
      return lastInsertedId();
    }
    if (codeRows.size() != 1
        || nameRows.size() != 1
        || !matchesSku(codeRows.getFirst(), fixture, productId)) {
      throw conflict("SKU " + fixture.skuCode());
    }
    if (!fixture.name().equals(codeRows.getFirst().get("name")) && context.writes()) {
      jdbcTemplate.update(
          "UPDATE skus SET name=? WHERE id=?", fixture.name(), number(codeRows.getFirst(), "id"));
    }
    return number(codeRows.getFirst(), "id");
  }

  private void ensureInventory(SkuFixture fixture, long skuId, ImportContext context) {
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT sku_id,available_quantity,reserved_quantity,version FROM inventories WHERE"
                + " sku_id=? FOR UPDATE",
            skuId);
    if (rows.isEmpty()) {
      context.createdInventories++;
      if (context.writes()) {
        jdbcTemplate.update(
            "INSERT INTO inventories(sku_id,available_quantity,reserved_quantity,version) VALUES"
                + " (?,?,0,0)",
            skuId,
            fixture.initialInventory());
      }
      return;
    }
    if (rows.size() != 1
        || number(rows.getFirst(), "sku_id") != skuId
        || number(rows.getFirst(), "available_quantity") < 0
        || number(rows.getFirst(), "reserved_quantity") < 0
        || number(rows.getFirst(), "version") < 0) {
      throw conflict("inventory " + fixture.skuCode());
    }
    // Inventory is mutable commerce state. Re-running the manifest never resets it.
  }

  private void ensurePlan(PlanFixture fixture, Map<String, Long> skuIds, ImportContext context) {
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            """
            SELECT id,plan_key,name,target_pet_type,on_sale,sale_starts_on,sale_ends_on,current_plan_version_id
            FROM subscription_plans
            WHERE plan_key=?
            FOR UPDATE
            """,
            fixture.planKey());
    if (rows.isEmpty()) {
      List<Map<String, Object>> nameRows =
          jdbcTemplate.queryForList(
              "SELECT"
                  + " id,plan_key,name,target_pet_type,on_sale,sale_starts_on,sale_ends_on,current_plan_version_id"
                  + " FROM subscription_plans WHERE name=? FOR UPDATE",
              fixture.name());
      if (nameRows.size() == 1
          && String.valueOf(nameRows.getFirst().get("plan_key")).startsWith("legacy-plan-")) {
        if (context.writes()) {
          jdbcTemplate.update(
              "UPDATE subscription_plans SET plan_key=? WHERE id=?",
              fixture.planKey(),
              number(nameRows.getFirst(), "id"));
        }
        rows = nameRows;
      }
      if (!nameRows.isEmpty() && rows.isEmpty()) throw conflict("plan " + fixture.planKey());
      if (rows.isEmpty()) {
        context.createdPlans++;
        if (context.writes()) createPlan(fixture, skuIds);
        return;
      }
    }
    if (rows.size() != 1) {
      throw conflict("plan " + fixture.name());
    }
    if (!fixture.name().equals(rows.getFirst().get("name")) && context.writes()) {
      jdbcTemplate.update(
          "UPDATE subscription_plans SET name=? WHERE id=?",
          fixture.name(),
          number(rows.getFirst(), "id"));
    }
    validatePlan(rows.getFirst(), fixture, skuIds);
  }

  private void createPlan(PlanFixture fixture, Map<String, Long> skuIds) {
    jdbcTemplate.update(
        "INSERT INTO"
            + " subscription_plans(plan_key,name,target_pet_type,on_sale,sale_starts_on,sale_ends_on,current_plan_version_id)"
            + " VALUES (?,?,?,true,NULL,NULL,NULL)",
        fixture.planKey(),
        fixture.name(),
        fixture.petType());
    long planId = lastInsertedId();
    jdbcTemplate.update(
        "INSERT INTO plan_versions(plan_id,package_price_krw,is_migration_only) VALUES (?,?,false)",
        planId,
        fixture.packagePriceKrw());
    long planVersionId = lastInsertedId();
    for (PlanItemFixture item : fixture.items()) {
      Long skuId = skuIds.get(item.skuCode());
      if (skuId == null || skuId < 0) throw conflict("plan item " + item.skuCode());
      jdbcTemplate.update(
          "INSERT INTO plan_items(plan_version_id,sku_id,quantity) VALUES (?,?,?)",
          planVersionId,
          skuId,
          item.quantity());
    }
    for (Integer cycle : DELIVERY_CYCLES) {
      jdbcTemplate.update(
          "INSERT INTO plan_version_delivery_cycles(plan_version_id,delivery_cycle_weeks) VALUES"
              + " (?,?)",
          planVersionId,
          cycle);
    }
    jdbcTemplate.update(
        "UPDATE subscription_plans SET current_plan_version_id=? WHERE id=?",
        planVersionId,
        planId);
  }

  private void validatePlan(
      Map<String, Object> row, PlanFixture fixture, Map<String, Long> skuIds) {
    if (!fixture.petType().equals(row.get("target_pet_type"))
        || !trueValue(row.get("on_sale"))
        || row.get("sale_starts_on") != null
        || row.get("sale_ends_on") != null
        || !(row.get("current_plan_version_id") instanceof Number currentVersion)) {
      throw conflict("plan " + fixture.name());
    }
    long planId = number(row, "id");
    long planVersionId = currentVersion.longValue();
    Integer versionCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM plan_versions WHERE plan_id=?", Integer.class, planId);
    List<Map<String, Object>> versions =
        jdbcTemplate.queryForList(
            "SELECT id,plan_id,package_price_krw,is_migration_only FROM plan_versions WHERE id=?"
                + " AND plan_id=?",
            planVersionId,
            planId);
    Integer itemCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM plan_items WHERE plan_version_id=?",
            Integer.class,
            planVersionId);
    Integer cycleCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM plan_version_delivery_cycles WHERE plan_version_id=?",
            Integer.class,
            planVersionId);
    Map<Long, Integer> actualItems = new HashMap<>();
    for (Map<String, Object> item :
        jdbcTemplate.queryForList(
            "SELECT sku_id,quantity FROM plan_items WHERE plan_version_id=?", planVersionId)) {
      actualItems.put(number(item, "sku_id"), Math.toIntExact(number(item, "quantity")));
    }
    Map<Long, Integer> expectedItems = new HashMap<>();
    for (PlanItemFixture item : fixture.items()) {
      Long skuId = skuIds.get(item.skuCode());
      if (skuId == null || skuId < 0) throw conflict("plan item " + item.skuCode());
      expectedItems.put(skuId, item.quantity());
    }
    List<Integer> cycles =
        jdbcTemplate.queryForList(
            "SELECT delivery_cycle_weeks FROM plan_version_delivery_cycles WHERE plan_version_id=?"
                + " ORDER BY delivery_cycle_weeks",
            Integer.class,
            planVersionId);
    if (versionCount == null
        || versionCount != 1
        || versions.size() != 1
        || !matchesPlanVersion(
            versions.getFirst(), planVersionId, planId, fixture.packagePriceKrw())
        || itemCount == null
        || itemCount != expectedItems.size()
        || !expectedItems.equals(actualItems)
        || cycleCount == null
        || cycleCount != DELIVERY_CYCLE_COUNT
        || !DELIVERY_CYCLES.equals(cycles)) {
      throw conflict("plan " + fixture.name());
    }
  }

  private DemoCatalogImportPostflight postflight(CatalogManifest manifest) {
    List<String> categorySlugs = manifest.categories().stream().map(CategoryFixture::slug).toList();
    List<String> catalogKeys =
        manifest.products().stream().map(ProductFixture::catalogKey).toList();
    List<String> skuCodes =
        manifest.products().stream()
            .flatMap(product -> product.skus().stream())
            .map(SkuFixture::skuCode)
            .toList();
    List<String> planKeys = manifest.plans().stream().map(PlanFixture::planKey).toList();
    long expectedItems = manifest.plans().stream().mapToLong(plan -> plan.items().size()).sum();
    long expectedCycles = (long) manifest.plans().size() * DELIVERY_CYCLE_COUNT;
    long actualCategories =
        countBy("SELECT COUNT(*) FROM categories WHERE slug IN (%s)", categorySlugs);
    long actualProducts =
        countBy("SELECT COUNT(*) FROM products WHERE catalog_key IN (%s)", catalogKeys);
    long actualSkus = countBy("SELECT COUNT(*) FROM skus WHERE sku_code IN (%s)", skuCodes);
    long actualInventories =
        countBy(
            "SELECT COUNT(*) FROM inventories inventory JOIN skus sku ON sku.id=inventory.sku_id"
                + " WHERE sku.sku_code IN (%s)",
            skuCodes);
    long actualPlans =
        countBy("SELECT COUNT(*) FROM subscription_plans WHERE plan_key IN (%s)", planKeys);
    long actualVersions =
        countBy(
            "SELECT COUNT(*) FROM plan_versions version JOIN subscription_plans plan ON"
                + " plan.id=version.plan_id WHERE plan.plan_key IN (%s)",
            planKeys);
    long actualItems =
        countBy(
            "SELECT COUNT(*) FROM plan_items item JOIN plan_versions version ON"
                + " version.id=item.plan_version_id JOIN subscription_plans plan ON"
                + " plan.id=version.plan_id WHERE plan.plan_key IN (%s)",
            planKeys);
    long actualCycles =
        countBy(
            "SELECT COUNT(*) FROM plan_version_delivery_cycles cycle JOIN plan_versions version ON"
                + " version.id=cycle.plan_version_id JOIN subscription_plans plan ON"
                + " plan.id=version.plan_id WHERE plan.plan_key IN (%s)",
            planKeys);
    return new DemoCatalogImportPostflight(
        categorySlugs.size(),
        actualCategories,
        catalogKeys.size(),
        actualProducts,
        skuCodes.size(),
        actualSkus,
        skuCodes.size(),
        actualInventories,
        planKeys.size(),
        actualPlans,
        planKeys.size(),
        actualVersions,
        expectedItems,
        actualItems,
        expectedCycles,
        actualCycles);
  }

  private long countBy(String queryTemplate, List<String> values) {
    if (values.isEmpty()) return 0;
    String placeholders = String.join(",", Collections.nCopies(values.size(), "?"));
    Long count =
        jdbcTemplate.queryForObject(
            queryTemplate.formatted(placeholders), Long.class, values.toArray());
    return count == null ? 0 : count;
  }

  private boolean matchesCategory(Map<String, Object> row, CategoryFixture fixture) {
    return fixture.name().equals(row.get("name"))
        && fixture.slug().equals(row.get("slug"))
        && number(row, "display_order") == fixture.displayOrder()
        && trueValue(row.get("active"));
  }

  private boolean matchesProduct(
      Map<String, Object> row, ProductFixture fixture, long categoryId, long brandId) {
    return fixture.catalogKey().equals(row.get("catalog_key"))
        && matchesProductFields(row, fixture, categoryId, brandId, false, false);
  }

  private boolean matchesProductFields(
      Map<String, Object> row,
      ProductFixture fixture,
      long categoryId,
      long brandId,
      boolean allowLegacyImage,
      boolean allowNameChange) {
    return number(row, "category_id") == categoryId
        && number(row, "brand_id") == brandId
        && (allowNameChange || fixture.name().equals(row.get("name")))
        && fixture.shortDescription().equals(row.get("short_description"))
        && fixture.description().equals(row.get("description"))
        && fixture.petType().equals(row.get("pet_type"))
        && (java.util.Objects.equals(fixture.thumbnailUrl(), row.get("thumbnail_url"))
            || correction.acceptsProductThumbnail(fixture.catalogKey(), row.get("thumbnail_url"))
            || (allowLegacyImage && row.get("thumbnail_url") == null))
        && "PUBLIC".equals(row.get("display_status"));
  }

  private boolean matchesSku(Map<String, Object> row, SkuFixture fixture, long productId) {
    return number(row, "product_id") == productId
        && fixture.skuCode().equals(row.get("sku_code"))
        && fixture.price().compareTo(new BigDecimal(row.get("price").toString())) == 0
        && fixture.subscribable() == trueValue(row.get("subscribable"))
        && number(row, "display_order") == fixture.displayOrder()
        && fixture.status().equals(row.get("status"));
  }

  private boolean matchesPlanVersion(
      Map<String, Object> row, long versionId, long planId, long price) {
    return number(row, "id") == versionId
        && number(row, "plan_id") == planId
        && number(row, "package_price_krw") == price
        && !trueValue(row.get("is_migration_only"));
  }

  private long lastInsertedId() {
    return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }

  private CatalogManifestImportException conflict(String fixture) {
    return new CatalogManifestImportException(
        "Demo Catalog manifest conflicts with existing data: " + fixture);
  }

  private static long number(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).longValue();
  }

  private static boolean trueValue(Object value) {
    if (value instanceof Boolean booleanValue) return booleanValue;
    return value instanceof Number number && number.intValue() == 1;
  }

  private CatalogManifest loadManifest(String manifestLocation) {
    try {
      String location =
          manifestLocation == null || manifestLocation.isBlank()
              ? DEFAULT_MANIFEST_LOCATION
              : manifestLocation;
      Resource resource = new DefaultResourceLoader().getResource(location);
      CatalogManifest manifest =
          objectMapper.readValue(resource.getInputStream(), CatalogManifest.class);
      if (manifest.version() != 1
          || manifest.categories() == null
          || manifest.products() == null
          || manifest.plans() == null) {
        throw new CatalogManifestImportException(
            "Demo Catalog manifest version or required lists are invalid");
      }
      Set<String> categorySlugs = new HashSet<>();
      for (CategoryFixture category : manifest.categories()) {
        if (category == null || category.slug() == null || !categorySlugs.add(category.slug())) {
          throw new CatalogManifestImportException(
              "Demo Catalog manifest category business keys are duplicated or missing");
        }
      }
      Set<String> catalogKeys = new HashSet<>();
      Set<String> skuCodes = new HashSet<>();
      for (ProductFixture product : manifest.products()) {
        if (product == null
            || product.catalogKey() == null
            || !catalogKeys.add(product.catalogKey())
            || product.categorySlug() == null
            || !categorySlugs.contains(product.categorySlug())
            || product.skus() == null
            || product.skus().isEmpty()) {
          throw new CatalogManifestImportException(
              "Demo Catalog manifest product or SKU business keys are duplicated or missing");
        }
        for (SkuFixture sku : product.skus()) {
          if (sku == null || sku.skuCode() == null || !skuCodes.add(sku.skuCode())) {
            throw new CatalogManifestImportException(
                "Demo Catalog manifest SKU business keys are duplicated or missing");
          }
        }
      }
      Set<String> planKeys = new HashSet<>();
      for (PlanFixture plan : manifest.plans()) {
        if (plan == null
            || plan.planKey() == null
            || !planKeys.add(plan.planKey())
            || plan.items() == null
            || plan.items().isEmpty()) {
          throw new CatalogManifestImportException(
              "Demo Catalog manifest plan business keys or items are duplicated or missing");
        }
        Set<String> planSkuCodes = new HashSet<>();
        for (PlanItemFixture item : plan.items()) {
          if (item == null
              || item.skuCode() == null
              || !skuCodes.contains(item.skuCode())
              || !planSkuCodes.add(item.skuCode())) {
            throw new CatalogManifestImportException(
                "Demo Catalog manifest plan item SKU is duplicated, missing, or unknown");
          }
        }
      }
      return manifest;
    } catch (CatalogManifestImportException exception) {
      throw exception;
    } catch (IOException | RuntimeException exception) {
      throw new CatalogManifestImportException("Demo Catalog manifest cannot be read", exception);
    }
  }

  private final class ImportContext {
    private final DemoCatalogImportOperation operation;
    private long nextVirtualId = -1;
    private int createdCategories;
    private int createdProducts;
    private int createdSkus;
    private int createdInventories;
    private int createdPlans;

    private ImportContext(DemoCatalogImportOperation operation) {
      this.operation = operation;
    }

    private boolean writes() {
      return operation == DemoCatalogImportOperation.APPLY;
    }

    private long virtualId() {
      return nextVirtualId--;
    }

    private DemoCatalogImportResult result(DemoCatalogImportPostflight postflight) {
      return new DemoCatalogImportResult(
          operation,
          createdCategories,
          createdProducts,
          createdSkus,
          createdInventories,
          createdPlans,
          postflight);
    }
  }

  private record CatalogManifest(
      int version,
      List<CategoryFixture> categories,
      List<ProductFixture> products,
      List<PlanFixture> plans) {}

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

  private record SkuFixture(
      String skuCode,
      String name,
      BigDecimal price,
      boolean subscribable,
      int displayOrder,
      String status,
      int initialInventory) {}

  private record PlanFixture(
      String planKey,
      String name,
      String petType,
      long packagePriceKrw,
      List<PlanItemFixture> items) {}

  private record PlanItemFixture(String skuCode, int quantity) {}
}
