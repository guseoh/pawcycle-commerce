package com.pawcycle.backend.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.pawcycle.backend.catalog.admin.api.*;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminPersistence;
import com.pawcycle.backend.catalog.maintenance.persistence.CustomerCatalogImportPersistence;
import com.pawcycle.backend.catalog.maintenance.persistence.CustomerCatalogRealismCorrectionPersistence;
import com.pawcycle.backend.catalog.maintenance.persistence.DemoCatalogImportPersistence;
import com.pawcycle.backend.catalog.product.application.ProductListCache;
import com.pawcycle.backend.catalog.product.application.ProductListCacheInvalidator;
import jakarta.validation.Validator;
import jakarta.validation.Validation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@ActiveProfiles("test")
class CustomerCatalogInvalidManifestIntegrationTests {
  @Autowired JdbcTemplate jdbc;
  @Autowired DemoCatalogImportPersistence baseline;
  @Autowired CatalogAdminPersistence expansion;
  @Autowired ProductListCacheInvalidator invalidator;
  @Autowired CustomerCatalogRealismCorrectionPersistence correction;
  @Autowired Validator validator;
  @Autowired PlatformTransactionManager transactionManager;
  @MockitoSpyBean ProductListCache cache;
  @TempDir Path directory;
  private final ObjectMapper mapper = new ObjectMapper();

  static Stream<Arguments> ineffectiveCommandConstraints() {
    return Stream.of(
        Arguments.of("/products/0/optionGroups/0/name", " "),
        Arguments.of("/products/0/optionGroups/0/values/0", " "),
        Arguments.of("/products/0/images/1/imageUrl", " "),
        Arguments.of("/products/0/images/1/displayOrder", -1));
  }

  @ParameterizedTest
  @MethodSource("ineffectiveCommandConstraints")
  void customManifestRejectsPreviouslyUnvalidatedCommandFields(String pointer, Object value)
      throws Exception {
    assertRejected(pointer, value);
  }

  // Derive invalid boundaries from the actual HTTP metadata. A changed HTTP constraint must
  // remain enforced by the independent Import boundary; production never imports these DTOs.
  static Stream<Arguments> httpConstraintViolations() {
    List<Arguments> cases = new ArrayList<>();
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      for (HttpField field : httpFields()) {
        var invalid = new LinkedHashSet<Object>();
        var property = validator.getConstraintsForClass(field.type()).getConstraintsForProperty(field.name());
        for (var constraint : property.getConstraintDescriptors()) {
          switch (constraint.getAnnotation().annotationType().getSimpleName()) {
            case "NotBlank" -> { invalid.add(null); invalid.add(""); invalid.add(" \t"); }
            case "NotNull" -> invalid.add(null);
            case "Size" -> invalid.add("a".repeat((int) constraint.getAttributes().get("max") + 1));
            case "Pattern" -> invalid.add("invalid?");
            case "DecimalMin" -> invalid.add(new BigDecimal("-0.01"));
            case "Digits" -> {
              invalid.add(new BigDecimal("10000000000"));
              invalid.add(new BigDecimal("1.001"));
            }
            case "PositiveOrZero" -> invalid.add(-1);
            default -> throw new AssertionError("Unmapped HTTP constraint: " + constraint.getAnnotation());
          }
        }
        for (Object value : invalid) cases.add(Arguments.of(field, value));
      }
    }
    return cases.stream();
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("httpConstraintViolations")
  void importPreservesHttpFieldRejectionStrength(HttpField field, Object value) throws Exception {
    assertThat(validator.validateValue(field.type(), field.name(), value)).isNotEmpty();
    assertRejected(field.pointer(), value);
  }

  static Stream<Arguments> acceptedBoundaries() {
    List<Arguments> cases = new ArrayList<>();
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      for (HttpField field : httpFields()) {
        var property = factory.getValidator().getConstraintsForClass(field.type())
            .getConstraintsForProperty(field.name());
        for (var constraint : property.getConstraintDescriptors()) {
          if (constraint.getAnnotation().annotationType().getSimpleName().equals("Size")
              && !field.name().equals("petType")) {
            int max = (int) constraint.getAttributes().get("max");
            String value = field.name().equals("skuCode") ? "QA3-" + "a".repeat(max - 4) : "a".repeat(max);
            cases.add(Arguments.of(field.pointer(), value));
          }
        }
      }
    }
    for (String pointer : List.of("/products/0/description", "/products/0/thumbnailUrl",
        "/products/0/skus/0/compareAtPrice", "/products/0/images/0/altText")) {
      cases.add(Arguments.of(pointer, null));
    }
    cases.add(Arguments.of("/products/0/skus/0/price", new BigDecimal("0.00")));
    cases.add(Arguments.of("/products/0/skus/0/compareAtPrice", new BigDecimal("9999999999.99")));
    return cases.stream();
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("acceptedBoundaries")
  void importAcceptsHttpMaximumLengthsAndOptionalNulls(String pointer, Object value) throws Exception {
    var service = service(pointer, value);
    var before = snapshot();
    new TransactionTemplate(transactionManager).execute(status -> service.validate());
    assertThat(snapshot()).isEqualTo(before);
    // service and supplement are constructed directly, so their annotations are not proxies.
    // This explicit outer transaction owns baseline -> supplement -> correction, including
    // proxied REQUIRED delegates and afterCommit registrations on the shared DataSource.
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      status.setRollbackOnly();
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      var result = service.apply();
      assertThat(result.baseline().operation()).isEqualTo(DemoCatalogImportOperation.APPLY);
      assertThat(result.supplement().operation()).isEqualTo(CustomerCatalogImportOperation.APPLY);
      assertThat(result.correction().operation()).isEqualTo(CustomerCatalogRealismOperation.APPLY);
      var applied = snapshot();
      assertThat(applied).isNotEqualTo(before);
      assertThat(applied.get("inventories")).containsAll(before.get("inventories"));
      assertThat(jdbc.queryForObject("SELECT name FROM brands WHERE slug='pawcycle-demo-catalog'", String.class))
          .isEqualTo("PawCycle");
      assertPersistedBoundary(pointer, value);
      verify(cache, never()).invalidate();
    });
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    assertThat(snapshot()).isEqualTo(before);
    verify(cache, never()).invalidate();
  }

  private void assertPersistedBoundary(String pointer, Object expected) {
    JsonNode manifest = mapper.readTree(directory.resolve("custom-manifest.json").toFile());
    String[] parts = pointer.substring(1).split("/");
    String field = parts[parts.length - 1];
    Map<String, Object> row;
    switch (parts[0]) {
      case "brands", "categories" -> row = jdbc.queryForMap(
          "SELECT * FROM " + parts[0] + " WHERE slug=?", manifest.at("/" + parts[0] + "/0/slug").asString());
      case "facets" -> {
        String key = manifest.at("/facets/0/key").asString();
        if (parts.length == 3) row = jdbc.queryForMap("SELECT * FROM facet_definitions WHERE `key`=?", key);
        else {
          row = jdbc.queryForMap("SELECT o.* FROM facet_options o JOIN facet_definitions d ON d.id=o.facet_definition_id WHERE d.`key`=? AND o.display_order=0", key);
          field = "value";
        }
      }
      case "products" -> {
        String key = manifest.at("/products/0/catalogKey").asString();
        if (parts.length == 3) row = jdbc.queryForMap("SELECT * FROM products WHERE catalog_key=?", key);
        else row = switch (parts[2]) {
          case "skus" -> jdbc.queryForMap("SELECT * FROM skus WHERE sku_code=?", manifest.at("/products/0/skus/0/skuCode").asString());
          case "images" -> jdbc.queryForMap("SELECT i.* FROM product_images i JOIN products p ON p.id=i.product_id WHERE p.catalog_key=? AND i.image_type=? AND i.display_order=?",
              key, manifest.at("/products/0/images/0/imageType").asString(), manifest.at("/products/0/images/0/displayOrder").asInt());
          case "detailSections" -> jdbc.queryForMap("SELECT d.* FROM product_detail_sections d JOIN products p ON p.id=d.product_id WHERE p.catalog_key=? AND d.display_order=?",
              key, manifest.at("/products/0/detailSections/0/displayOrder").asInt());
          case "optionGroups" -> {
            if (parts.length == 5) yield jdbc.queryForMap("SELECT g.* FROM product_option_groups g JOIN products p ON p.id=g.product_id WHERE p.catalog_key=? AND g.display_order=0", key);
            field = "value";
            yield jdbc.queryForMap("SELECT v.* FROM product_option_values v JOIN product_option_groups g ON g.id=v.option_group_id JOIN products p ON p.id=g.product_id WHERE p.catalog_key=? AND g.display_order=0 AND v.display_order=0", key);
          }
          default -> throw new AssertionError("Unmapped persisted boundary: " + pointer);
        };
      }
      default -> throw new AssertionError("Unmapped persisted boundary: " + pointer);
    }
    String column = field.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
    Object actual = row.get(column);
    assertThat(row).as(pointer).containsKey(column);
    if (expected instanceof BigDecimal decimal) assertThat(new BigDecimal(actual.toString())).as(pointer).isEqualByComparingTo(decimal);
    else assertThat(actual).as(pointer).isEqualTo(expected);
  }

  static Stream<Arguments> malformedStructuresAndRelations() {
    List<Arguments> cases = new ArrayList<>();
    for (String pointer : List.of("/version", "/brands", "/brands/0", "/brands/0/displayOrder",
        "/categories", "/categories/0", "/categories/0/displayOrder", "/categories/0/facets",
        "/categories/0/facets/0", "/facets", "/facets/0", "/facets/0/values", "/facets/0/values/0",
        "/products", "/products/0", "/products/0/optionGroups", "/products/0/optionGroups/0",
        "/products/0/optionGroups/0/values", "/products/0/optionGroups/0/values/0",
        "/products/0/skus", "/products/0/skus/0", "/products/0/skus/0/selectedOptions",
        "/products/0/skus/0/selectedOptions/0", "/products/0/skus/0/initialInventory",
        "/products/0/images", "/products/0/images/0", "/products/0/facets",
        "/products/0/detailSections", "/products/0/detailSections/0")) {
      cases.add(Arguments.of(pointer, null));
    }
    for (String pointer : List.of("/version", "/brands/0/displayOrder", "/categories/0/displayOrder",
        "/products/0/skus/0/subscribable", "/products/0/skus/0/initialInventory",
        "/products/0/images/0/imageType", "/products/0/images/0/displayOrder",
        "/products/0/detailSections/0/body", "/products/0/detailSections/0/visible")) {
      cases.add(Arguments.of(pointer, Missing.FIELD));
    }
    cases.addAll(List.of(
        Arguments.of("/products/0/skus/0/initialInventory", -1),
        Arguments.of("/brands/1/slug", "grain-tail"),
        Arguments.of("/products/1/catalogKey", "qa3-dog-salmon-small"),
        Arguments.of("/products/0/skus/1/skuCode", "QA3-DOG-SALMON-SMALL-1"),
        Arguments.of("/categories/0/parentSlug", "food-dry"),
        Arguments.of("/categories/0/parentSlug", "missing-parent"),
        Arguments.of("/categories/1/parentSlug", "food-dry"),
        Arguments.of("/categories/0/facets", List.of("life-stage", "life-stage")),
        Arguments.of("/products/0/brandSlug", "missing-brand"),
        Arguments.of("/products/0/categorySlug", "missing-category"),
        Arguments.of("/products/0/petType", "BIRD"),
        Arguments.of("/products/0/facets", Map.of("protein", "missing-option")),
        Arguments.of("/products/0/facets", Map.of("missing-facet", "missing-option")),
        Arguments.of("/products/0/optionGroups/0/values", List.of()),
        Arguments.of("/products/0/optionGroups/0/values", List.of("1kg", "1kg")),
        Arguments.of("/products/0/skus/0/selectedOptions", List.of("missing", "1팩")),
        Arguments.of("/products/0/skus/0/compareAtPrice", new BigDecimal("8900")),
        Arguments.of("/products/0/images/1/imageType", "MAIN"),
        Arguments.of("/products/0/images/1/displayOrder", 0),
        Arguments.of("/products/0/detailSections/0/visible", false),
        Arguments.of("/products/0/detailSections/0/body", "<p>html</p>"),
        Arguments.of("/products/0/detailSections/1/displayOrder", 0)));
    return cases.stream();
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("malformedStructuresAndRelations")
  void invalidNestedStructureAndRelationsFailClosed(String pointer, Object value) throws Exception {
    assertRejected(pointer, value);
  }

  private void assertRejected(String pointer, Object value) throws Exception {
    CustomerCatalogImportService service = service(pointer, value);
    var before = snapshot();
    var transaction = new TransactionTemplate(transactionManager);

    assertThatThrownBy(() -> transaction.execute(status -> service.validate()))
        .isInstanceOf(CatalogManifestImportException.class);
    assertThat(snapshot()).isEqualTo(before);
    // Baseline apply really runs first; its writes must also roll back on invalid supplement input.
    assertThatThrownBy(() -> transaction.execute(status -> service.apply()))
        .isInstanceOf(CatalogManifestImportException.class);
    assertThat(snapshot()).isEqualTo(before);
    verify(cache, never()).invalidate();
  }

  private CustomerCatalogImportService service(String pointer, Object value) throws Exception {
    JsonNode manifest;
    try (var input = new ClassPathResource("catalog/customer-catalog-v3.json").getInputStream()) {
      manifest = mapper.readTree(input);
    }
    int separator = pointer.lastIndexOf('/');
    var parent = manifest.at(pointer.substring(0, separator));
    String field = pointer.substring(separator + 1);
    var previous = manifest.at(pointer);
    if (value == Missing.FIELD) ((ObjectNode) parent).remove(field);
    else if (parent instanceof ObjectNode object) object.set(field, mapper.valueToTree(value));
    else ((ArrayNode) parent).set(Integer.parseInt(field), mapper.valueToTree(value));
    // Keep references coherent for scalar field tests (not deliberately malformed relations).
    if (previous.isString() && value instanceof String replacement
        && !pointer.endsWith("Slug") && !pointer.endsWith("catalogKey") && !pointer.endsWith("skuCode")
        && !pointer.endsWith("parentSlug") && !pointer.endsWith("imageType")) {
      // Shared stock-photo URLs are content, not cross-product foreign keys. Keep a
      // product's thumbnail/MAIN image coherent without rewriting unrelated correction targets.
      JsonNode referenceScope = pointer.startsWith("/products/")
              && (pointer.endsWith("/thumbnailUrl") || pointer.endsWith("/imageUrl"))
          ? manifest.at("/products/" + pointer.split("/")[2]) : manifest;
      replaceReferences(referenceScope, previous.asString(), replacement);
    }
    Path file = directory.resolve("custom-manifest.json");
    Files.writeString(file, mapper.writeValueAsString(manifest));
    var supplement = new CustomerCatalogImportPersistence(
        jdbc, expansion, invalidator, correction, file.toUri().toString());
    return new CustomerCatalogImportService(baseline, supplement, correction);
  }

  private void replaceReferences(JsonNode node, String old, String replacement) {
    if (node instanceof ObjectNode object) {
      for (String key : new ArrayList<>(object.propertyNames())) {
        JsonNode child = object.get(key);
        if (child.isString() && child.asString().equals(old)) object.put(key, replacement);
        else replaceReferences(child, old, replacement);
        if (key.equals(old)) { object.remove(key); object.set(replacement, child); }
      }
    } else if (node instanceof ArrayNode array) {
      for (int i = 0; i < array.size(); i++) {
        JsonNode child = array.get(i);
        if (child.isString() && child.asString().equals(old)) array.set(i, mapper.valueToTree(replacement));
        else replaceReferences(child, old, replacement);
      }
    }
  }

  private record HttpField(Class<?> type, String name, String pointer) {}
  private enum Missing { FIELD }

  private static List<HttpField> httpFields() {
    List<HttpField> fields = new ArrayList<>();
    add(fields, BrandCreateRequest.class, "/brands/0/", "name", "slug", "displayOrder");
    add(fields, CategoryCreateRequest.class, "/categories/0/", "name", "slug", "displayOrder");
    add(fields, ProductCreateRequest.class, "/products/0/", "name", "shortDescription", "description", "petType", "thumbnailUrl");
    add(fields, SkuCreateRequest.class, "/products/0/skus/0/", "skuCode", "name", "price", "compareAtPrice", "subscribable");
    add(fields, FacetDefinitionCreateRequest.class, "/facets/0/", "key", "name");
    fields.add(new HttpField(FacetOptionCreateRequest.class, "value", "/facets/0/values/0"));
    add(fields, OptionGroupCreateRequest.class, "/products/0/optionGroups/0/", "name");
    fields.add(new HttpField(OptionValueCreateRequest.class, "value", "/products/0/optionGroups/0/values/0"));
    add(fields, ImageCreateRequest.class, "/products/0/images/0/", "imageUrl", "altText", "displayOrder", "imageType");
    add(fields, DetailSectionCreateRequest.class, "/products/0/detailSections/0/", "title", "body", "displayOrder", "visible");
    return fields;
  }

  private static void add(List<HttpField> fields, Class<?> type, String prefix, String... names) {
    for (String name : names) fields.add(new HttpField(type, name, prefix + name));
  }

  private Map<String, List<Map<String, Object>>> snapshot() {
    Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
    for (String table : List.of("brands", "categories", "products", "skus", "inventories",
        "product_images", "product_option_groups", "product_option_values", "sku_option_values",
        "facet_definitions", "facet_options", "category_facets", "product_facet_values",
        "product_detail_sections", "admin_audit_logs", "subscription_plans", "plan_versions",
        "plan_items", "plan_version_delivery_cycles")) {
      result.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY 1,2"));
    }
    return result;
  }
}
