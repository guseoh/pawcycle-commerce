package com.pawcycle.backend.foundation.bootstrap;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Supplemental, opt-in local QA data. Never a production import manifest. */
@Service
@Profile("local-integration & !test & !production & !prod")
public class LocalCustomerCatalogV3FixtureService {
    private final JdbcTemplate jdbc;
    private final LocalCommerceDemoFixtureService baseline;
    private final CatalogExpansionAdminService expansion;
    private final ProductListCacheInvalidator cache;
    private final Validator validator;

    public LocalCustomerCatalogV3FixtureService(JdbcTemplate jdbc, LocalCommerceDemoFixtureService baseline,
            CatalogExpansionAdminService expansion, ProductListCacheInvalidator cache, Validator validator) {
        this.jdbc = jdbc;
        this.baseline = baseline;
        this.expansion = expansion;
        this.cache = cache;
        this.validator = validator;
    }

    @Transactional
    public void bootstrap() {
        Manifest manifest = load();
        validate(manifest);
        baseline.bootstrap();
        // Lock the existing V1 brand to serialize concurrent V3 bootstrap attempts.
        jdbc.queryForObject("SELECT id FROM brands WHERE slug='pawcycle-demo-catalog' FOR UPDATE", Long.class);
        Map<String, Long> brands = new HashMap<>();
        for (Brand brand : manifest.brands()) {
            brands.put(brand.slug(), ensure("brands", fields("slug", brand.slug()),
                    fields("name", brand.name(), "display_order", brand.displayOrder(), "active", true, "logo_url", null)));
        }
        Map<String, Long> definitions = new HashMap<>();
        Map<String, Long> options = new HashMap<>();
        for (Facet facet : manifest.facets()) {
            long id = ensure("facet_definitions", fields("key", facet.key()), fields("name", facet.name()));
            definitions.put(facet.key(), id);
            for (int i = 0; i < facet.values().size(); i++) {
                String value = facet.values().get(i);
                options.put(facet.key() + ":" + value, ensure("facet_options",
                        fields("facet_definition_id", id, "value", value), fields("display_order", i)));
            }
        }
        for (Category category : manifest.categories()) {
            Long parent = category.parentSlug() == null ? null : categoryId(category.parentSlug());
            if (parent != null && jdbc.queryForObject("SELECT parent_id FROM categories WHERE id=?", Long.class, parent) != null) {
                throw conflict("category depth " + category.slug());
            }
            long id = ensure("categories", fields("slug", category.slug()), fields("name", category.name(),
                    "parent_id", parent, "display_order", category.displayOrder(), "active", true));
            for (int i = 0; i < category.facets().size(); i++) {
                Map<String, Object> key = fields("category_id", id, "facet_definition_id", definitions.get(category.facets().get(i)));
                int order = i;
                ensureRow("category_facets", key, fields("display_order", i), () -> {
                    expansion.assignCategoryFacet(id, definitions.get(category.facets().get(order)), new AdminCatalogRequests.CategoryFacetAssign(order));
                    return 0;
                });
            }
        }
        for (Product product : manifest.products()) importProduct(product, brands, options);
        cache.invalidateAfterCommit();
    }

    private void importProduct(Product p, Map<String, Long> brands, Map<String, Long> facets) {
        long id = ensure("products", fields("catalog_key", p.catalogKey()), fields(
                "category_id", categoryId(p.categorySlug()), "brand_id", brands.get(p.brandSlug()),
                "name", p.name(), "short_description", p.shortDescription(), "description", p.description(),
                "pet_type", p.petType(), "thumbnail_url", p.thumbnailUrl(), "display_status", "PUBLIC"));
        Map<String, Long> values = new HashMap<>();
        for (int i = 0; i < p.optionGroups().size(); i++) {
            Group group = p.optionGroups().get(i);
            int order = i;
            long groupId = ensureRow("product_option_groups", fields("product_id", id, "name", group.name()),
                    fields("display_order", i), () -> expansion.createOptionGroup(id,
                            new AdminCatalogRequests.OptionGroupCreate(group.name(), order)).optionGroupId());
            for (int j = 0; j < group.values().size(); j++) {
                String value = group.values().get(j);
                int valueOrder = j;
                values.put(i + ":" + value, ensureRow("product_option_values", fields("option_group_id", groupId, "value", value),
                        fields("display_order", j), () -> expansion.createOptionValue(id, groupId,
                                new AdminCatalogRequests.OptionValueCreate(value, valueOrder)).optionValueId()));
            }
        }
        for (int i = 0; i < p.skus().size(); i++) {
            Sku sku = p.skus().get(i);
            long skuId = ensure("skus", fields("sku_code", sku.skuCode()), fields("product_id", id, "name", sku.name(),
                    "price", sku.price(), "compare_at_price", sku.compareAtPrice(), "subscribable", sku.subscribable(),
                    "display_order", i, "status", "ACTIVE"));
            List<Map<String, Object>> inventory = rows("inventories", fields("sku_id", skuId));
            if (inventory.isEmpty()) {
                insert("inventories", fields("sku_id", skuId, "available_quantity", sku.initialInventory(), "reserved_quantity", 0, "version", 0));
            } else if (inventory.size() != 1 || ((Number) inventory.getFirst().get("available_quantity")).longValue() < 0
                    || ((Number) inventory.getFirst().get("reserved_quantity")).longValue() < 0
                    || ((Number) inventory.getFirst().get("version")).longValue() < 0) {
                throw conflict("inventory " + sku.skuCode());
            }
            // Existing inventory is mutable commerce state: never replenish or reset reservations.
            List<Long> selected = new ArrayList<>();
            for (int j = 0; j < sku.selectedOptions().size(); j++) selected.add(values.get(j + ":" + sku.selectedOptions().get(j)));
            ensureLinks("sku_option_values", "sku_id", skuId, "option_value_id", selected,
                    () -> expansion.setSkuOptionValues(id, skuId, new AdminCatalogRequests.SkuOptionValues(selected)));
        }
        for (AdminCatalogRequests.ImageCreate image : p.images()) {
            ensureRow("product_images", fields("product_id", id, "display_order", image.displayOrder()),
                    fields("image_url", image.imageUrl(), "alt_text", image.altText(), "image_type", image.imageType()),
                    () -> expansion.createImage(id, image).imageId());
        }
        for (AdminCatalogRequests.DetailSectionCreate section : p.detailSections()) {
            Map<String, Object> key = fields("product_id", id, "display_order", section.displayOrder());
            Map<String, Object> content = fields("title", section.title(), "body", section.body(), "visible", section.visible());
            ensureRow("product_detail_sections", key, content, () -> {
                Map<String, Object> row = new LinkedHashMap<>(key);
                row.putAll(content);
                Timestamp now = Timestamp.from(Instant.now());
                row.putAll(fields("created_at", now, "updated_at", now));
                return insert("product_detail_sections", row);
            });
        }
        List<Long> selectedFacets = p.facets().entrySet().stream().map(e -> facets.get(e.getKey() + ":" + e.getValue())).toList();
        ensureLinks("product_facet_values", "product_id", id, "facet_option_id", selectedFacets,
                () -> expansion.setProductFacetValues(id, new AdminCatalogRequests.ProductFacetValues(selectedFacets)));
    }

    private void ensureLinks(String table, String ownerColumn, long owner, String valueColumn, List<Long> expected, Runnable create) {
        List<Long> actual = jdbc.queryForList("SELECT " + valueColumn + " FROM " + table + " WHERE " + ownerColumn + "=?", Long.class, owner);
        if (actual.isEmpty() && !expected.isEmpty()) create.run();
        else if (!new HashSet<>(actual).equals(new HashSet<>(expected))) throw conflict(table + " relationship");
    }

    private long ensure(String table, Map<String, Object> key, Map<String, Object> content) {
        return ensureRow(table, key, content, () -> {
            Map<String, Object> row = new LinkedHashMap<>(key);
            row.putAll(content);
            return insert(table, row);
        });
    }

    private long ensureRow(String table, Map<String, Object> key, Map<String, Object> content, LongSupplier create) {
        List<Map<String, Object>> rows = rows(table, key);
        if (rows.isEmpty()) return create.getAsLong();
        if (rows.size() != 1) throw conflict(table + " business key");
        Map<String, Object> row = rows.getFirst();
        for (Map.Entry<String, Object> field : content.entrySet()) {
            if (!equal(field.getValue(), row.get(field.getKey()))) throw conflict(table + " " + field.getKey());
        }
        return row.get("id") instanceof Number number ? number.longValue() : 0;
    }

    // Table/column identifiers originate only in this class; all fixture values are bound parameters.
    private List<Map<String, Object>> rows(String table, Map<String, Object> key) {
        String where = key.keySet().stream().map(k -> "`" + k + "`=?").collect(java.util.stream.Collectors.joining(" AND "));
        return jdbc.queryForList("SELECT * FROM " + table + " WHERE " + where + " FOR UPDATE", key.values().toArray());
    }

    private long insert(String table, Map<String, Object> row) {
        String columns = row.keySet().stream().map(k -> "`" + k + "`").collect(java.util.stream.Collectors.joining(","));
        jdbc.update("INSERT INTO " + table + "(" + columns + ") VALUES (" + String.join(",", java.util.Collections.nCopies(row.size(), "?")) + ")", row.values().toArray());
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long categoryId(String slug) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM categories WHERE slug=? AND active=true", Long.class, slug);
        if (ids.size() != 1) throw conflict("category " + slug);
        return ids.getFirst();
    }

    private static Map<String, Object> fields(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }

    private static boolean equal(Object expected, Object actual) {
        if (expected instanceof Number a && actual instanceof Number b) return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0;
        if (expected instanceof Boolean a && actual instanceof Number b) return a == (b.intValue() == 1);
        return Objects.equals(expected, actual);
    }

    private Manifest load() {
        try (var input = new ClassPathResource("catalog/customer-catalog-v3.json").getInputStream()) {
            return new ObjectMapper().readValue(input, Manifest.class);
        } catch (IOException | RuntimeException exception) {
            throw new LocalQaBootstrapException("Customer Catalog V3 fixture를 읽을 수 없습니다", exception);
        }
    }

    private void validate(Manifest m) {
        if (m.version() != 3 || m.products() == null || m.products().isEmpty()) throw conflict("manifest version/products");
        Set<String> brandKeys = new HashSet<>();
        for (Brand brand : m.brands()) {
            valid(new AdminCatalogRequests.BrandCreate(brand.name(), brand.slug(), null, true, brand.displayOrder()));
            if (!brandKeys.add(brand.slug())) throw conflict("duplicate brand");
        }
        Map<String, Set<String>> facets = new HashMap<>();
        for (Facet facet : m.facets()) {
            valid(new AdminCatalogRequests.FacetDefinitionCreate(facet.key(), facet.name()));
            if (facet.values().isEmpty() || new HashSet<>(facet.values()).size() != facet.values().size()
                    || facets.put(facet.key(), new HashSet<>(facet.values())) != null) throw conflict("duplicate facet");
            for (String value : facet.values()) valid(new AdminCatalogRequests.FacetOptionCreate(value, 0));
        }
        Map<String, Category> categories = new HashMap<>();
        Set<String> roots = Set.of("food", "treats", "hygiene", "toilet");
        for (Category category : m.categories()) {
            valid(new AdminCatalogRequests.CategoryCreate(category.name(), category.slug(), category.displayOrder(), true));
            if (roots.contains(category.slug()) || categories.put(category.slug(), category) != null
                    || !facets.keySet().containsAll(category.facets())) throw conflict("category definition");
            if (category.parentSlug() != null && !roots.contains(category.parentSlug())
                    && (!categories.containsKey(category.parentSlug()) || categories.get(category.parentSlug()).parentSlug() != null)) {
                throw conflict("category parent/depth");
            }
        }
        Set<String> keys = new HashSet<>();
        Set<String> codes = new HashSet<>();
        for (Product p : m.products()) {
            valid(new AdminCatalogRequests.ProductCreate(1L, 1L, p.name(), p.shortDescription(), p.description(), p.petType(), p.thumbnailUrl()));
            if (!p.catalogKey().matches("qa3-[a-z0-9-]+") || !keys.add(p.catalogKey()) || !brandKeys.contains(p.brandSlug())
                    || !categories.containsKey(p.categorySlug()) || !Set.of("DOG", "CAT").contains(p.petType())
                    || p.optionGroups().size() > 2 || p.skus().isEmpty()) throw conflict("product " + p.catalogKey());
            Set<String> groupNames = new HashSet<>();
            for (Group group : p.optionGroups()) {
                valid(new AdminCatalogRequests.OptionGroupCreate(group.name(), 0));
                if (!groupNames.add(group.name()) || group.values().isEmpty() || new HashSet<>(group.values()).size() != group.values().size()) throw conflict("option group");
                for (String value : group.values()) valid(new AdminCatalogRequests.OptionValueCreate(value, 0));
            }
            Set<List<String>> combinations = new HashSet<>();
            for (Sku sku : p.skus()) {
                valid(new AdminCatalogRequests.SkuCreate(sku.skuCode(), sku.name(), sku.price(), sku.compareAtPrice(), sku.subscribable(), 0, SkuStatus.ACTIVE));
                if (!sku.skuCode().startsWith("QA3-") || !codes.add(sku.skuCode()) || sku.initialInventory() < 0
                        || (sku.compareAtPrice() != null && sku.compareAtPrice().compareTo(sku.price()) <= 0)
                        || sku.selectedOptions().size() != p.optionGroups().size() || !combinations.add(sku.selectedOptions())) throw conflict("SKU " + sku.skuCode());
                for (int i = 0; i < sku.selectedOptions().size(); i++) {
                    if (!p.optionGroups().get(i).values().contains(sku.selectedOptions().get(i))) throw conflict("SKU option value");
                }
            }
            Set<Integer> imageOrders = new HashSet<>();
            for (var image : p.images()) {
                valid(image);
                if (!imageOrders.add(image.displayOrder())) throw conflict("image order");
            }
            if (p.images().stream().filter(i -> i.imageType().equals("MAIN")).count() != 1) throw conflict("MAIN image");
            Set<Integer> sectionOrders = new HashSet<>();
            for (var section : p.detailSections()) {
                valid(section);
                if (!section.visible() || !sectionOrders.add(section.displayOrder()) || section.body().contains("<") || section.body().contains(">")) throw conflict("detail section");
            }
            if (p.detailSections().size() < 3 || p.detailSections().size() > 4) throw conflict("detail section count");
            for (var facet : p.facets().entrySet()) {
                if (!categories.get(p.categorySlug()).facets().contains(facet.getKey())
                        || !facets.get(facet.getKey()).contains(facet.getValue())) throw conflict("category/facet compatibility");
            }
        }
    }

    private void valid(Object request) {
        if (!validator.validate(request).isEmpty()) throw conflict("invalid " + request.getClass().getSimpleName());
    }

    private static LocalQaBootstrapException conflict(String key) {
        return new LocalQaBootstrapException("Customer Catalog V3 fixture 충돌: " + key);
    }

    private record Manifest(int version, List<Brand> brands, List<Category> categories, List<Facet> facets, List<Product> products) {}
    private record Brand(String slug, String name, int displayOrder) {}
    private record Category(String slug, String name, String parentSlug, int displayOrder, List<String> facets) {}
    private record Facet(String key, String name, List<String> values) {}
    private record Group(String name, List<String> values) {}
    private record Sku(String skuCode, String name, BigDecimal price, BigDecimal compareAtPrice, boolean subscribable, int initialInventory, List<String> selectedOptions) {}
    private record Product(String catalogKey, String brandSlug, String categorySlug, String name, String shortDescription,
            String description, String petType, String thumbnailUrl, List<Group> optionGroups, List<Sku> skus,
            List<AdminCatalogRequests.ImageCreate> images, Map<String, String> facets, List<AdminCatalogRequests.DetailSectionCreate> detailSections) {}
}
