package com.pawcycle.backend.catalog.application;

import com.pawcycle.backend.catalog.admin.api.AdminCatalogRequests;
import com.pawcycle.backend.catalog.admin.application.CatalogExpansionAdminService;
import com.pawcycle.backend.catalog.product.application.ProductListCacheInvalidator;
import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import jakarta.validation.Validator;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Imports the shared Customer Catalog V3 supplement used by local QA and approved one-shot catalog operations. */
@Service
public class CustomerCatalogV3ImportService {

    public static final String DEFAULT_MANIFEST_LOCATION = "classpath:catalog/customer-catalog-v3.json";
    private static final Set<String> BASELINE_ROOT_CATEGORIES = Set.of("food", "treats", "hygiene", "toilet");

    private final JdbcTemplate jdbc;
    private final CatalogExpansionAdminService expansion;
    private final ProductListCacheInvalidator cache;
    private final Validator validator;
    private final ObjectMapper objectMapper;
    private final String manifestLocation;

    @Autowired
    public CustomerCatalogV3ImportService(
            JdbcTemplate jdbc,
            CatalogExpansionAdminService expansion,
            ProductListCacheInvalidator cache,
            Validator validator,
            @Value("${pawcycle.catalog.customer.manifest:" + DEFAULT_MANIFEST_LOCATION + "}") String manifestLocation) {
        this.jdbc = jdbc;
        this.expansion = expansion;
        this.cache = cache;
        this.validator = validator;
        this.objectMapper = new ObjectMapper();
        this.manifestLocation = manifestLocation;
    }

    public CustomerCatalogV3ImportService(
            JdbcTemplate jdbc,
            CatalogExpansionAdminService expansion,
            ProductListCacheInvalidator cache,
            Validator validator) {
        this(jdbc, expansion, cache, validator, DEFAULT_MANIFEST_LOCATION);
    }

    @Transactional
    public ImportResult validate() {
        return process(Operation.VALIDATE);
    }

    @Transactional
    public ImportResult apply() {
        return process(Operation.APPLY);
    }

    private ImportResult process(Operation operation) {
        Manifest manifest = load();
        validateManifest(manifest);
        ImportContext context = new ImportContext(operation);

        if (context.writes()) {
            List<Long> baselineBrand = jdbc.queryForList(
                    "SELECT id FROM brands WHERE slug='pawcycle-demo-catalog' FOR UPDATE", Long.class);
            if (baselineBrand.size() != 1) throw conflict("baseline brand");
        }

        Map<String, Long> categoryIds = new HashMap<>();
        for (String root : BASELINE_ROOT_CATEGORIES) {
            categoryIds.put(root, baselineRootCategoryId(root, context));
        }

        Map<String, Long> brands = new HashMap<>();
        for (Brand brand : manifest.brands()) {
            brands.put(brand.slug(), ensure(context, "brands", fields("slug", brand.slug()),
                    fields("name", brand.name(), "display_order", brand.displayOrder(), "active", true, "logo_url", null)));
        }

        Map<String, Long> definitions = new HashMap<>();
        Map<String, Long> options = new HashMap<>();
        for (Facet facet : manifest.facets()) {
            long id = ensure(context, "facet_definitions", fields("key", facet.key()), fields("name", facet.name()));
            definitions.put(facet.key(), id);
            for (int i = 0; i < facet.values().size(); i++) {
                String value = facet.values().get(i);
                options.put(facet.key() + ":" + value, ensure(context, "facet_options",
                        fields("facet_definition_id", id, "value", value), fields("display_order", i)));
            }
        }

        for (Category category : manifest.categories()) {
            Long parent = category.parentSlug() == null ? null : categoryIds.get(category.parentSlug());
            if (category.parentSlug() != null && parent == null) throw conflict("category parent " + category.slug());
            long id = ensure(context, "categories", fields("slug", category.slug()), fields(
                    "name", category.name(), "parent_id", parent, "display_order", category.displayOrder(), "active", true));
            categoryIds.put(category.slug(), id);
            for (int i = 0; i < category.facets().size(); i++) {
                long definitionId = definitions.get(category.facets().get(i));
                Map<String, Object> key = fields("category_id", id, "facet_definition_id", definitionId);
                int order = i;
                ensureRow(context, "category_facets", key, fields("display_order", i), () -> {
                    expansion.assignCategoryFacet(id, definitionId, new AdminCatalogRequests.CategoryFacetAssign(order));
                    return 0;
                });
            }
        }

        for (Product product : manifest.products()) {
            importProduct(context, product, brands, categoryIds, options);
        }
        if (context.writes()) cache.invalidateAfterCommit();
        return context.result(manifest);
    }

    private long baselineRootCategoryId(String slug, ImportContext context) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,parent_id,active FROM categories WHERE slug=? FOR UPDATE", slug);
        if (rows.isEmpty()) {
            if (context.writes()) throw conflict("baseline category " + slug);
            return context.virtualId();
        }
        if (rows.size() != 1 || rows.getFirst().get("parent_id") != null || !trueValue(rows.getFirst().get("active"))) {
            throw conflict("baseline category " + slug);
        }
        return number(rows.getFirst().get("id"));
    }

    private void importProduct(
            ImportContext context,
            Product product,
            Map<String, Long> brands,
            Map<String, Long> categoryIds,
            Map<String, Long> facets) {
        Long categoryId = categoryIds.get(product.categorySlug());
        Long brandId = brands.get(product.brandSlug());
        if (categoryId == null || brandId == null) throw conflict("product relation " + product.catalogKey());

        long productId = ensure(context, "products", fields("catalog_key", product.catalogKey()), fields(
                "category_id", categoryId, "brand_id", brandId,
                "name", product.name(), "short_description", product.shortDescription(), "description", product.description(),
                "pet_type", product.petType(), "thumbnail_url", product.thumbnailUrl(), "display_status", "PUBLIC"));

        Map<String, Long> values = new HashMap<>();
        for (int i = 0; i < product.optionGroups().size(); i++) {
            Group group = product.optionGroups().get(i);
            int order = i;
            long groupId = ensureRow(context, "product_option_groups", fields("product_id", productId, "name", group.name()),
                    fields("display_order", i), () -> expansion.createOptionGroup(productId,
                            new AdminCatalogRequests.OptionGroupCreate(group.name(), order)).optionGroupId());
            for (int j = 0; j < group.values().size(); j++) {
                String value = group.values().get(j);
                int valueOrder = j;
                values.put(i + ":" + value, ensureRow(context, "product_option_values",
                        fields("option_group_id", groupId, "value", value), fields("display_order", j),
                        () -> expansion.createOptionValue(productId, groupId,
                                new AdminCatalogRequests.OptionValueCreate(value, valueOrder)).optionValueId()));
            }
        }

        for (int i = 0; i < product.skus().size(); i++) {
            Sku sku = product.skus().get(i);
            long skuId = ensure(context, "skus", fields("sku_code", sku.skuCode()), fields(
                    "product_id", productId, "name", sku.name(), "price", sku.price(),
                    "compare_at_price", sku.compareAtPrice(), "subscribable", sku.subscribable(),
                    "display_order", i, "status", "ACTIVE"));
            List<Map<String, Object>> inventory = rows("inventories", fields("sku_id", skuId));
            if (inventory.isEmpty()) {
                context.recordMissing("inventories");
                if (context.writes()) {
                    insert("inventories", fields("sku_id", skuId, "available_quantity", sku.initialInventory(),
                            "reserved_quantity", 0, "version", 0));
                }
            } else if (inventory.size() != 1
                    || number(inventory.getFirst().get("available_quantity")) < 0
                    || number(inventory.getFirst().get("reserved_quantity")) < 0
                    || number(inventory.getFirst().get("version")) < 0) {
                throw conflict("inventory " + sku.skuCode());
            }

            List<Long> selected = new ArrayList<>();
            for (int j = 0; j < sku.selectedOptions().size(); j++) {
                Long optionId = values.get(j + ":" + sku.selectedOptions().get(j));
                if (optionId == null) throw conflict("SKU option value " + sku.skuCode());
                selected.add(optionId);
            }
            ensureLinks(context, "sku_option_values", "sku_id", skuId, "option_value_id", selected,
                    () -> expansion.setSkuOptionValues(productId, skuId, new AdminCatalogRequests.SkuOptionValues(selected)));
        }

        ensureImages(context, productId, product.images());
        ensureDetailSections(context, productId, product.detailSections());
        List<Long> selectedFacets = product.facets().entrySet().stream()
                .map(entry -> facets.get(entry.getKey() + ":" + entry.getValue()))
                .toList();
        if (selectedFacets.stream().anyMatch(Objects::isNull)) throw conflict("product facet " + product.catalogKey());
        ensureLinks(context, "product_facet_values", "product_id", productId, "facet_option_id", selectedFacets,
                () -> expansion.setProductFacetValues(productId, new AdminCatalogRequests.ProductFacetValues(selectedFacets)));
    }

    private void ensureImages(ImportContext context, long productId, List<AdminCatalogRequests.ImageCreate> expected) {
        List<Map<String, Object>> actual = jdbc.queryForList("""
                SELECT image_url,alt_text,display_order,image_type
                FROM product_images
                WHERE product_id=?
                ORDER BY display_order,id
                FOR UPDATE
                """, productId);
        if (actual.isEmpty()) {
            context.recordMissing("product_images", expected.size());
            if (context.writes()) {
                expected.stream()
                        .sorted(Comparator.comparingInt(AdminCatalogRequests.ImageCreate::displayOrder))
                        .forEach(image -> expansion.createImage(productId, image));
            }
            return;
        }
        List<Map<String, Object>> expectedRows = expected.stream()
                .sorted(Comparator.comparingInt(AdminCatalogRequests.ImageCreate::displayOrder))
                .map(image -> fields("image_url", image.imageUrl(), "alt_text", image.altText(),
                        "display_order", image.displayOrder(), "image_type", image.imageType()))
                .toList();
        if (!sameRows(expectedRows, actual)) throw conflict("product_images collection");
    }

    private void ensureDetailSections(
            ImportContext context,
            long productId,
            List<AdminCatalogRequests.DetailSectionCreate> expected) {
        List<Map<String, Object>> actual = jdbc.queryForList("""
                SELECT title,body,display_order,visible
                FROM product_detail_sections
                WHERE product_id=?
                ORDER BY display_order,id
                FOR UPDATE
                """, productId);
        if (actual.isEmpty()) {
            context.recordMissing("product_detail_sections", expected.size());
            if (context.writes()) {
                for (AdminCatalogRequests.DetailSectionCreate section : expected.stream()
                        .sorted(Comparator.comparingInt(AdminCatalogRequests.DetailSectionCreate::displayOrder)).toList()) {
                    Map<String, Object> row = fields("product_id", productId, "title", section.title(), "body", section.body(),
                            "display_order", section.displayOrder(), "visible", section.visible());
                    Timestamp now = Timestamp.from(Instant.now());
                    row.putAll(fields("created_at", now, "updated_at", now));
                    insert("product_detail_sections", row);
                }
            }
            return;
        }
        List<Map<String, Object>> expectedRows = expected.stream()
                .sorted(Comparator.comparingInt(AdminCatalogRequests.DetailSectionCreate::displayOrder))
                .map(section -> fields("title", section.title(), "body", section.body(),
                        "display_order", section.displayOrder(), "visible", section.visible()))
                .toList();
        if (!sameRows(expectedRows, actual)) throw conflict("product_detail_sections collection");
    }

    private boolean sameRows(List<Map<String, Object>> expected, List<Map<String, Object>> actual) {
        if (expected.size() != actual.size()) return false;
        for (int i = 0; i < expected.size(); i++) {
            for (Map.Entry<String, Object> field : expected.get(i).entrySet()) {
                if (!equal(field.getValue(), actual.get(i).get(field.getKey()))) return false;
            }
        }
        return true;
    }

    private void ensureLinks(
            ImportContext context,
            String table,
            String ownerColumn,
            long owner,
            String valueColumn,
            List<Long> expected,
            Runnable create) {
        List<Long> actual = jdbc.queryForList(
                "SELECT " + valueColumn + " FROM " + table + " WHERE " + ownerColumn + "=?", Long.class, owner);
        if (actual.isEmpty() && !expected.isEmpty()) {
            context.recordMissing(table, expected.size());
            if (context.writes()) create.run();
        } else if (!new HashSet<>(actual).equals(new HashSet<>(expected))) {
            throw conflict(table + " relationship");
        }
    }

    private long ensure(
            ImportContext context,
            String table,
            Map<String, Object> key,
            Map<String, Object> content) {
        return ensureRow(context, table, key, content, () -> {
            Map<String, Object> row = new LinkedHashMap<>(key);
            row.putAll(content);
            return insert(table, row);
        });
    }

    private long ensureRow(
            ImportContext context,
            String table,
            Map<String, Object> key,
            Map<String, Object> content,
            LongSupplier create) {
        List<Map<String, Object>> existing = rows(table, key);
        if (existing.isEmpty()) {
            context.recordMissing(table);
            return context.writes() ? create.getAsLong() : context.virtualId();
        }
        if (existing.size() != 1) throw conflict(table + " business key");
        Map<String, Object> row = existing.getFirst();
        for (Map.Entry<String, Object> field : content.entrySet()) {
            if (!equal(field.getValue(), row.get(field.getKey()))) throw conflict(table + " " + field.getKey());
        }
        return row.get("id") instanceof Number number ? number.longValue() : 0;
    }

    private List<Map<String, Object>> rows(String table, Map<String, Object> key) {
        String where = key.keySet().stream()
                .map(column -> "`" + column + "`=?")
                .collect(java.util.stream.Collectors.joining(" AND "));
        return jdbc.queryForList("SELECT * FROM " + table + " WHERE " + where + " FOR UPDATE", key.values().toArray());
    }

    private long insert(String table, Map<String, Object> row) {
        String columns = row.keySet().stream()
                .map(column -> "`" + column + "`")
                .collect(java.util.stream.Collectors.joining(","));
        jdbc.update("INSERT INTO " + table + "(" + columns + ") VALUES ("
                + String.join(",", java.util.Collections.nCopies(row.size(), "?")) + ")", row.values().toArray());
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private Manifest load() {
        try {
            String location = manifestLocation == null || manifestLocation.isBlank()
                    ? DEFAULT_MANIFEST_LOCATION : manifestLocation;
            Resource resource = new DefaultResourceLoader().getResource(location);
            return objectMapper.readValue(resource.getInputStream(), Manifest.class);
        } catch (CatalogManifestImportException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new CatalogManifestImportException("Customer Catalog V3 manifest cannot be read", exception);
        }
    }

    private void validateManifest(Manifest manifest) {
        if (manifest == null || manifest.version() != 3 || manifest.products() == null || manifest.products().isEmpty()) {
            throw conflict("manifest version/products");
        }
        if (manifest.brands() == null || manifest.categories() == null || manifest.facets() == null) {
            throw conflict("manifest required lists");
        }

        Set<String> brandKeys = new HashSet<>();
        for (Brand brand : manifest.brands()) {
            valid(new AdminCatalogRequests.BrandCreate(brand.name(), brand.slug(), null, true, brand.displayOrder()));
            if (!brandKeys.add(brand.slug())) throw conflict("duplicate brand");
        }

        Map<String, Set<String>> facets = new HashMap<>();
        for (Facet facet : manifest.facets()) {
            valid(new AdminCatalogRequests.FacetDefinitionCreate(facet.key(), facet.name()));
            if (facet.values() == null || facet.values().isEmpty()
                    || new HashSet<>(facet.values()).size() != facet.values().size()
                    || facets.put(facet.key(), new HashSet<>(facet.values())) != null) {
                throw conflict("duplicate facet");
            }
            for (String value : facet.values()) valid(new AdminCatalogRequests.FacetOptionCreate(value, 0));
        }

        Map<String, Category> categories = new HashMap<>();
        for (Category category : manifest.categories()) {
            valid(new AdminCatalogRequests.CategoryCreate(category.name(), category.slug(), category.displayOrder(), true));
            if (BASELINE_ROOT_CATEGORIES.contains(category.slug())
                    || categories.put(category.slug(), category) != null
                    || category.facets() == null
                    || !facets.keySet().containsAll(category.facets())) {
                throw conflict("category definition");
            }
            if (category.parentSlug() != null
                    && !BASELINE_ROOT_CATEGORIES.contains(category.parentSlug())
                    && (!categories.containsKey(category.parentSlug())
                    || categories.get(category.parentSlug()).parentSlug() != null)) {
                throw conflict("category parent/depth");
            }
        }

        Set<String> keys = new HashSet<>();
        Set<String> codes = new HashSet<>();
        for (Product product : manifest.products()) {
            valid(new AdminCatalogRequests.ProductCreate(
                    1L, 1L, product.name(), product.shortDescription(), product.description(), product.petType(), product.thumbnailUrl()));
            if (product.catalogKey() == null
                    || !product.catalogKey().matches("qa3-[a-z0-9-]+")
                    || !keys.add(product.catalogKey())
                    || !brandKeys.contains(product.brandSlug())
                    || !categories.containsKey(product.categorySlug())
                    || !Set.of("DOG", "CAT").contains(product.petType())
                    || product.optionGroups() == null
                    || product.optionGroups().size() > 2
                    || product.skus() == null
                    || product.skus().isEmpty()
                    || product.images() == null
                    || product.facets() == null
                    || product.detailSections() == null) {
                throw conflict("product " + product.catalogKey());
            }

            Set<String> groupNames = new HashSet<>();
            for (Group group : product.optionGroups()) {
                valid(new AdminCatalogRequests.OptionGroupCreate(group.name(), 0));
                if (!groupNames.add(group.name()) || group.values() == null || group.values().isEmpty()
                        || new HashSet<>(group.values()).size() != group.values().size()) {
                    throw conflict("option group");
                }
                for (String value : group.values()) valid(new AdminCatalogRequests.OptionValueCreate(value, 0));
            }

            Set<List<String>> combinations = new HashSet<>();
            for (Sku sku : product.skus()) {
                valid(new AdminCatalogRequests.SkuCreate(
                        sku.skuCode(), sku.name(), sku.price(), sku.compareAtPrice(), sku.subscribable(), 0, SkuStatus.ACTIVE));
                if (sku.skuCode() == null
                        || !sku.skuCode().startsWith("QA3-")
                        || !codes.add(sku.skuCode())
                        || sku.initialInventory() < 0
                        || (sku.compareAtPrice() != null && sku.compareAtPrice().compareTo(sku.price()) <= 0)
                        || sku.selectedOptions() == null
                        || sku.selectedOptions().size() != product.optionGroups().size()
                        || !combinations.add(List.copyOf(sku.selectedOptions()))) {
                    throw conflict("SKU " + sku.skuCode());
                }
                for (int i = 0; i < sku.selectedOptions().size(); i++) {
                    if (!product.optionGroups().get(i).values().contains(sku.selectedOptions().get(i))) {
                        throw conflict("SKU option value");
                    }
                }
            }

            Set<Integer> imageOrders = new HashSet<>();
            for (AdminCatalogRequests.ImageCreate image : product.images()) {
                valid(image);
                if (!imageOrders.add(image.displayOrder())) throw conflict("image order");
            }
            if (product.images().stream().filter(image -> image.imageType().equals("MAIN")).count() != 1) {
                throw conflict("MAIN image");
            }

            Set<Integer> sectionOrders = new HashSet<>();
            for (AdminCatalogRequests.DetailSectionCreate section : product.detailSections()) {
                valid(section);
                if (!section.visible() || !sectionOrders.add(section.displayOrder())
                        || section.body().contains("<") || section.body().contains(">")) {
                    throw conflict("detail section");
                }
            }
            if (product.detailSections().size() < 3 || product.detailSections().size() > 4) {
                throw conflict("detail section count");
            }

            for (Map.Entry<String, String> facet : product.facets().entrySet()) {
                if (!categories.get(product.categorySlug()).facets().contains(facet.getKey())
                        || !facets.get(facet.getKey()).contains(facet.getValue())) {
                    throw conflict("category/facet compatibility");
                }
            }
        }
    }

    private void valid(Object request) {
        if (!validator.validate(request).isEmpty()) throw conflict("invalid " + request.getClass().getSimpleName());
    }

    private static boolean equal(Object expected, Object actual) {
        if (expected instanceof Number a && actual instanceof Number b) {
            return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0;
        }
        if (expected instanceof Boolean a && actual instanceof Number b) return a == (b.intValue() == 1);
        return Objects.equals(expected, actual);
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }

    private static boolean trueValue(Object value) {
        if (value instanceof Boolean booleanValue) return booleanValue;
        return value instanceof Number number && number.intValue() == 1;
    }

    private static Map<String, Object> fields(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }

    private static CatalogManifestImportException conflict(String key) {
        return new CatalogManifestImportException("Customer Catalog V3 conflict: " + key);
    }

    public enum Operation {
        VALIDATE,
        APPLY
    }

    public record ImportResult(
            Operation operation,
            int expectedBrands,
            int expectedCategories,
            int expectedProducts,
            int expectedSkus,
            int brandsMissing,
            int categoriesMissing,
            int productsMissing,
            int skusMissing,
            int inventoriesMissing) {

        public String summary() {
            return "CUSTOMER_CATALOG_V3_IMPORT_RESULT operation=" + operation.name()
                    + " status=PASS expected=" + expectedBrands + "/" + expectedCategories + "/" + expectedProducts + "/" + expectedSkus
                    + " missing_or_created=" + brandsMissing + "/" + categoriesMissing + "/" + productsMissing + "/" + skusMissing
                    + " inventories_missing_or_created=" + inventoriesMissing;
        }
    }

    private final class ImportContext {
        private final Operation operation;
        private long nextVirtualId = -1;
        private int brandsMissing;
        private int categoriesMissing;
        private int productsMissing;
        private int skusMissing;
        private int inventoriesMissing;

        private ImportContext(Operation operation) {
            this.operation = operation;
        }

        private boolean writes() {
            return operation == Operation.APPLY;
        }

        private long virtualId() {
            return nextVirtualId--;
        }

        private void recordMissing(String table) {
            recordMissing(table, 1);
        }

        private void recordMissing(String table, int count) {
            switch (table) {
                case "brands" -> brandsMissing += count;
                case "categories" -> categoriesMissing += count;
                case "products" -> productsMissing += count;
                case "skus" -> skusMissing += count;
                case "inventories" -> inventoriesMissing += count;
                default -> {
                    // Supplemental relationship rows are validated for compatibility but omitted from the compact summary.
                }
            }
        }

        private ImportResult result(Manifest manifest) {
            int expectedSkus = manifest.products().stream().mapToInt(product -> product.skus().size()).sum();
            return new ImportResult(operation, manifest.brands().size(), manifest.categories().size(),
                    manifest.products().size(), expectedSkus, brandsMissing, categoriesMissing,
                    productsMissing, skusMissing, inventoriesMissing);
        }
    }

    private record Manifest(int version, List<Brand> brands, List<Category> categories, List<Facet> facets, List<Product> products) {}
    private record Brand(String slug, String name, int displayOrder) {}
    private record Category(String slug, String name, String parentSlug, int displayOrder, List<String> facets) {}
    private record Facet(String key, String name, List<String> values) {}
    private record Group(String name, List<String> values) {}
    private record Sku(String skuCode, String name, BigDecimal price, BigDecimal compareAtPrice,
            boolean subscribable, int initialInventory, List<String> selectedOptions) {}
    private record Product(String catalogKey, String brandSlug, String categorySlug, String name, String shortDescription,
            String description, String petType, String thumbnailUrl, List<Group> optionGroups, List<Sku> skus,
            List<AdminCatalogRequests.ImageCreate> images, Map<String, String> facets,
            List<AdminCatalogRequests.DetailSectionCreate> detailSections) {}
}
