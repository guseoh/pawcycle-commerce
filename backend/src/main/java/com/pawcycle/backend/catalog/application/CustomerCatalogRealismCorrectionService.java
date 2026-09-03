package com.pawcycle.backend.catalog.application;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
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

/**
 * Applies the small, guarded customer-facing content correction after the canonical catalog import.
 */
@Service
public class CustomerCatalogRealismCorrectionService {

  public static final String DEFAULT_MANIFEST_LOCATION =
      "classpath:catalog/customer-catalog-realism-v1.json";
  private final NativeQueryExecutor jdbc;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final String manifestLocation;
  private volatile Manifest loadedManifest;

  public CustomerCatalogRealismCorrectionService(NativeQueryExecutor jdbc) {
    this(jdbc, DEFAULT_MANIFEST_LOCATION);
  }

  @Autowired
  public CustomerCatalogRealismCorrectionService(
      NativeQueryExecutor jdbc,
      @Value("${pawcycle.catalog.customer.realism.manifest:" + DEFAULT_MANIFEST_LOCATION + "}")
          String manifestLocation) {
    this.jdbc = jdbc;
    this.manifestLocation = manifestLocation;
  }

  @Transactional
  public CustomerCatalogRealismImportResult validate() {
    Manifest manifest = load();
    validateManifest(manifest);
    validateExistingState(manifest);
    return new CustomerCatalogRealismImportResult(CustomerCatalogRealismOperation.VALIDATE, 0, 0, 0);
  }

  @Transactional
  public CustomerCatalogRealismImportResult apply() {
    Manifest manifest = load();
    validateManifest(manifest);
    int brands = applyBrand(manifest.brand());
    int products = 0;
    for (ProductCorrection correction : manifest.products()) products += applyProduct(correction);
    int images = 0;
    for (ImageCorrection correction : manifest.images()) images += applyImage(correction);
    return new CustomerCatalogRealismImportResult(
        CustomerCatalogRealismOperation.APPLY, brands, products, images);
  }

  boolean acceptsProductThumbnail(String catalogKey, Object actual) {
    return load().products().stream()
        .filter(product -> product.catalogKey().equals(catalogKey))
        .map(product -> product.thumbnail().desiredAfter())
        .anyMatch(desired -> same(actual, desired));
  }

  boolean acceptsImage(
      String catalogKey, String imageType, int displayOrder, Object imageUrl, Object altText) {
    return load().images().stream()
        .filter(
            image ->
                image.catalogKey().equals(catalogKey)
                    && image.imageType().equals(imageType)
                    && image.displayOrder() == displayOrder)
        .anyMatch(
            image ->
                same(imageUrl, image.imageUrl().desiredAfter())
                    && same(altText, image.altText().desiredAfter()));
  }

  private void validateExistingState(Manifest manifest) {
    List<Map<String, Object>> brandRows =
        jdbc.queryForList("SELECT id,name FROM brands WHERE slug=?", manifest.brand().slug());
    if (brandRows.size() > 1) throw conflict("brand " + manifest.brand().slug());
    if (!brandRows.isEmpty())
      checkValue(
          "brand " + manifest.brand().slug(),
          brandRows.getFirst().get("name"),
          manifest.brand().name());

    for (ProductCorrection correction : manifest.products()) {
      List<Map<String, Object>> rows =
          jdbc.queryForList(
              "SELECT id,thumbnail_url FROM products WHERE catalog_key=?", correction.catalogKey());
      if (rows.size() > 1) throw conflict("product " + correction.catalogKey());
      if (!rows.isEmpty())
        checkValue(
            "product thumbnail " + correction.catalogKey(),
            rows.getFirst().get("thumbnail_url"),
            correction.thumbnail());
    }
    for (ImageCorrection correction : manifest.images()) {
      List<Map<String, Object>> products =
          jdbc.queryForList("SELECT id FROM products WHERE catalog_key=?", correction.catalogKey());
      if (products.size() > 1) throw conflict("product " + correction.catalogKey());
      if (products.isEmpty()) continue;
      List<Map<String, Object>> rows =
          jdbc.queryForList(
              "SELECT image_url,alt_text FROM product_images WHERE product_id=? AND image_type=?"
                  + " AND display_order=?",
              number(products.getFirst(), "id"),
              correction.imageType(),
              correction.displayOrder());
      if (rows.size() > 1) throw conflict("image position " + correction.catalogKey());
      if (!rows.isEmpty()) {
        checkValue(
            "image URL " + correction.catalogKey(),
            rows.getFirst().get("image_url"),
            correction.imageUrl());
        checkValue(
            "image alt text " + correction.catalogKey(),
            rows.getFirst().get("alt_text"),
            correction.altText());
      }
    }
  }

  private int applyBrand(BrandCorrection correction) {
    List<Map<String, Object>> rows =
        jdbc.queryForList("SELECT id,name FROM brands WHERE slug=? FOR UPDATE", correction.slug());
    if (rows.size() != 1) throw conflict("brand " + correction.slug());
    Object current = rows.getFirst().get("name");
    if (same(current, correction.name().desiredAfter())) return 0;
    if (!same(current, correction.name().expectedBefore()))
      throw conflict("brand " + correction.slug());
    jdbc.update(
        "UPDATE brands SET name=? WHERE id=?",
        correction.name().desiredAfter(),
        number(rows.getFirst(), "id"));
    return 1;
  }

  private int applyProduct(ProductCorrection correction) {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT id,thumbnail_url FROM products WHERE catalog_key=? FOR UPDATE",
            correction.catalogKey());
    if (rows.size() != 1) throw conflict("product " + correction.catalogKey());
    Object current = rows.getFirst().get("thumbnail_url");
    if (same(current, correction.thumbnail().desiredAfter())) return 0;
    if (!same(current, correction.thumbnail().expectedBefore()))
      throw conflict("product thumbnail " + correction.catalogKey());
    jdbc.update(
        "UPDATE products SET thumbnail_url=? WHERE id=?",
        correction.thumbnail().desiredAfter(),
        number(rows.getFirst(), "id"));
    return 1;
  }

  private int applyImage(ImageCorrection correction) {
    List<Map<String, Object>> products =
        jdbc.queryForList(
            "SELECT id FROM products WHERE catalog_key=? FOR UPDATE", correction.catalogKey());
    if (products.size() != 1) throw conflict("product " + correction.catalogKey());
    long productId = number(products.getFirst(), "id");
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT id,image_url,alt_text FROM product_images WHERE product_id=? AND image_type=?"
                + " AND display_order=? FOR UPDATE",
            productId,
            correction.imageType(),
            correction.displayOrder());
    if (rows.size() != 1) throw conflict("image position " + correction.catalogKey());
    Object currentUrl = rows.getFirst().get("image_url");
    Object currentAlt = rows.getFirst().get("alt_text");
    if (same(currentUrl, correction.imageUrl().desiredAfter())
        && same(currentAlt, correction.altText().desiredAfter())) return 0;
    if (!same(currentUrl, correction.imageUrl().expectedBefore())
        || !same(currentAlt, correction.altText().expectedBefore())) {
      throw conflict("image " + correction.catalogKey());
    }
    jdbc.update(
        "UPDATE product_images SET image_url=?,alt_text=? WHERE id=?",
        correction.imageUrl().desiredAfter(),
        correction.altText().desiredAfter(),
        number(rows.getFirst(), "id"));
    return 1;
  }

  private void validateManifest(Manifest manifest) {
    if (manifest == null
        || manifest.version() != 1
        || manifest.brand() == null
        || manifest.products() == null
        || manifest.images() == null) {
      throw conflict("manifest version/shape");
    }
    if (manifest.brand().slug() == null || !manifest.brand().slug().matches("[a-z0-9-]+"))
      throw conflict("brand key");
    validateValue("brand name", manifest.brand().name());
    Set<String> productKeys = new HashSet<>();
    for (ProductCorrection product : manifest.products()) {
      if (product == null || product.catalogKey() == null || !productKeys.add(product.catalogKey()))
        throw conflict("duplicate product key");
      validateValue("thumbnail " + product.catalogKey(), product.thumbnail());
    }
    Set<String> imageKeys = new HashSet<>();
    for (ImageCorrection image : manifest.images()) {
      if (image == null
          || image.catalogKey() == null
          || !productKeys.contains(image.catalogKey())
          || !Set.of("MAIN", "DETAIL").contains(image.imageType())
          || image.displayOrder() < 0
          || !imageKeys.add(
              image.catalogKey() + "#" + image.imageType() + "#" + image.displayOrder())) {
        throw conflict("image key");
      }
      validateValue("image URL " + image.catalogKey(), image.imageUrl());
      validateValue("image alt text " + image.catalogKey(), image.altText());
    }
  }

  private void validateValue(String field, ValueCorrection value) {
    if (value == null
        || blank(value.expectedBefore())
        || blank(value.desiredAfter())
        || same(value.expectedBefore(), value.desiredAfter())) {
      throw conflict(field);
    }
    validateUrl(field, value.expectedBefore());
    validateUrl(field, value.desiredAfter());
  }

  private void validateValue(String field, TextCorrection value) {
    if (value == null
        || blank(value.expectedBefore())
        || blank(value.desiredAfter())
        || same(value.expectedBefore(), value.desiredAfter())) {
      throw conflict(field);
    }
  }

  private void validateUrl(String field, String value) {
    try {
      URI uri = URI.create(value);
      if (!"https".equals(uri.getScheme()) || !"images.unsplash.com".equals(uri.getHost()))
        throw conflict(field);
    } catch (IllegalArgumentException exception) {
      throw new CatalogManifestImportException(
          "Customer Catalog realism manifest URL is invalid: " + field, exception);
    }
  }

  private void checkValue(String field, Object actual, TextCorrection expected) {
    if (!same(actual, expected.expectedBefore()) && !same(actual, expected.desiredAfter()))
      throw conflict(field);
  }

  private void checkValue(String field, Object actual, ValueCorrection expected) {
    if (!same(actual, expected.expectedBefore()) && !same(actual, expected.desiredAfter()))
      throw conflict(field);
  }

  private Manifest load() {
    Manifest cached = loadedManifest;
    if (cached != null) return cached;
    synchronized (this) {
      if (loadedManifest != null) return loadedManifest;
      loadedManifest = readManifest();
      return loadedManifest;
    }
  }

  private Manifest readManifest() {
    try {
      String location =
          manifestLocation == null || manifestLocation.isBlank()
              ? DEFAULT_MANIFEST_LOCATION
              : manifestLocation;
      Resource resource = new DefaultResourceLoader().getResource(location);
      return objectMapper.readValue(resource.getInputStream(), Manifest.class);
    } catch (IOException | RuntimeException exception) {
      if (exception instanceof CatalogManifestImportException catalogException)
        throw catalogException;
      throw new CatalogManifestImportException(
          "Customer Catalog realism manifest cannot be read", exception);
    }
  }

  private CatalogManifestImportException conflict(String target) {
    return new CatalogManifestImportException(
        "Customer Catalog realism correction conflicts with existing data: " + target);
  }

  private static boolean same(Object actual, String expected) {
    return expected == null
        ? actual == null
        : expected.equals(actual == null ? null : actual.toString());
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static long number(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).longValue();
  }

  private record Manifest(
      int version,
      BrandCorrection brand,
      List<ProductCorrection> products,
      List<ImageCorrection> images) {}

  private record BrandCorrection(String slug, TextCorrection name) {}

  private record ProductCorrection(String catalogKey, ValueCorrection thumbnail) {}

  private record ImageCorrection(
      String catalogKey,
      String imageType,
      int displayOrder,
      ValueCorrection imageUrl,
      TextCorrection altText) {}

  private record TextCorrection(String expectedBefore, String desiredAfter) {}

  private record ValueCorrection(String expectedBefore, String desiredAfter) {}
}
