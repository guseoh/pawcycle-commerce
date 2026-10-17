package com.pawcycle.backend.catalog.admin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.pawcycle.backend.catalog.admin.application.AdminCatalogMutationService;
import com.pawcycle.backend.catalog.admin.application.AdminCatalogService;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.*;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.*;
import com.pawcycle.backend.catalog.product.application.ProductListCache;
import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.commerce.audit.application.AdminAuditService;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"spring.jpa.open-in-view=false", "spring.jpa.properties.hibernate.generate_statistics=true"})
@ActiveProfiles("test")
class CatalogAdminCoreMaterializationIntegrationTests {
  @Autowired private AdminCatalogService service;
  @Autowired private AdminCatalogMutationService mutations;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManagerFactory emf;
  @Autowired private ObjectMapper json;
  @MockitoSpyBean private ProductListCache cache;
  @MockitoSpyBean private AdminAuditService audits;
  private long adminId;
  private BrandView brand;
  private CategoryView parent;
  private CategoryView category;
  private ProductView product;
  private SkuView sku;

  @BeforeEach
  void setUp() {
    jdbc.update("INSERT INTO members(email,password_hash,role) VALUES ('core-boundary@example.test','fixture','ADMIN')");
    adminId = jdbc.queryForObject("SELECT id FROM members WHERE email='core-boundary@example.test'", Long.class);
    brand = mutations.createBrand(adminId, new BrandCreateCommand("core-brand", "core-brand", null, true, 5));
    parent = mutations.createCategory(adminId, new CategoryCreateCommand("core-parent", "core-parent", 9, true, null));
    category = mutations.createCategory(adminId, new CategoryCreateCommand("core-child", "core-child", 2, true, parent.categoryId()));
    product = mutations.createProduct(adminId, new ProductCreateCommand(category.categoryId(), brand.brandId(),
        "core-product", "short", "description", "DOG", "https://example.test/core"));
    sku = mutations.createSku(adminId, product.productId(), new SkuCreateCommand("CORE-SKU", "core-sku",
        new BigDecimal("12345.60"), new BigDecimal("23456.70"), false, 5, SkuStatus.ACTIVE));
  }

  @AfterEach
  void cleanUp() {
    jdbc.update("DELETE FROM admin_audit_logs WHERE admin_id=?", adminId);
    jdbc.update("DELETE FROM inventories WHERE sku_id=?", sku.skuId());
    jdbc.update("DELETE FROM skus WHERE id=?", sku.skuId());
    jdbc.update("DELETE FROM products WHERE id=?", product.productId());
    jdbc.update("DELETE FROM categories WHERE id=?", category.categoryId());
    jdbc.update("DELETE FROM categories WHERE id=?", parent.categoryId());
    jdbc.update("DELETE FROM brands WHERE id=?", brand.brandId());
    jdbc.update("DELETE FROM members WHERE id=?", adminId);
  }

  @Test
  void coreViewsAreDetachedScalarsAndListsNeedNoSqlOrEntityLoadsForHttpConversion() throws Exception {
    List<Object> views = new ArrayList<>(List.of(brand, parent, category, product, sku));
    views.add(service.category(category.categoryId()));
    views.add(service.product(product.productId()));
    var brands = service.brands();
    var categories = service.categories();
    var statistics = emf.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    var products = service.products();
    assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    var skus = service.skus(product.productId());
    assertThat(products).filteredOn(v -> v.productId().equals(product.productId())).containsExactly(product);
    assertThat(brands).filteredOn(v -> v.brandId().equals(brand.brandId())).containsExactly(brand);
    assertThat(categories).filteredOn(v -> v.categoryId().equals(category.categoryId()) || v.categoryId().equals(parent.categoryId()))
        .extracting(CategoryView::categoryId).containsExactly(category.categoryId(), parent.categoryId());
    assertThat(skus).containsExactly(sku);
    assertThat(product.brandId()).isNotEqualTo(1L);
    assertThat(category.parentId()).isEqualTo(parent.categoryId());
    assertThat(sku.price()).isEqualTo(new BigDecimal("12345.60"));
    assertThat(sku.compareAtPrice()).isEqualTo(new BigDecimal("23456.70"));
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    assertThat(TransactionSynchronizationManager.hasResource(emf)).isFalse();
    statistics.clear();
    for (Object view : views) {
      for (var field : view.getClass().getRecordComponents()) {
        Object value = field.getAccessor().invoke(view);
        assertThat(value == null || value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Enum<?>).isTrue();
      }
      Object expected = CatalogAdminCoreMappingTests.legacyResponse(view);
      Object actual = switch (view) {
        case BrandView v -> BrandResponse.from(v);
        case CategoryView v -> CategoryResponse.from(v);
        case ProductView v -> ProductResponse.from(v);
        case SkuView v -> SkuResponse.from(v);
        default -> throw new AssertionError(view);
      };
      assertThat(actual).isEqualTo(expected);
      assertThat(json.writeValueAsString(actual)).isEqualTo(json.writeValueAsString(expected));
    }
    assertThat(BrandListResponse.from(brands).brands()).isEqualTo(brands.stream().map(CatalogAdminCoreMappingTests::legacyResponse).toList());
    assertThat(CategoryListResponse.from(categories).categories()).isEqualTo(categories.stream().map(CatalogAdminCoreMappingTests::legacyResponse).toList());
    assertThat(ProductListResponse.from(products).products()).isEqualTo(products.stream().map(CatalogAdminCoreMappingTests::legacyResponse).toList());
    assertThat(SkuListResponse.from(skus).skus()).isEqualTo(skus.stream().map(CatalogAdminCoreMappingTests::legacyResponse).toList());
    json.writeValueAsString(List.of(BrandListResponse.from(brands), CategoryListResponse.from(categories),
        ProductListResponse.from(products), SkuListResponse.from(skus)));
    assertThat(statistics.getPrepareStatementCount()).isZero();
    assertThat(statistics.getEntityLoadCount()).isZero();
  }

  @Test
  void generatedIdsAuditTargetsInventoryAndExplicitNullPatchesCommitTogether() throws Exception {
    for (var target : List.of(Map.entry("BRAND", brand.brandId()), Map.entry("CATEGORY", category.categoryId()),
        Map.entry("PRODUCT", product.productId()), Map.entry("SKU", sku.skuId()))) {
      assertThat(target.getValue()).isPositive();
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE admin_id=? AND target_type=? AND target_id=? AND action=?",
          Integer.class, adminId, target.getKey(), target.getValue(), "CATALOG_" + target.getKey() + "_CREATE")).isEqualTo(1);
    }
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventories WHERE sku_id=?", Integer.class, sku.skuId())).isEqualTo(1);
    clearInvocations(cache);
    var categoryPatch = CatalogAdminCommandMapping.command(json.readValue("{\"parentId\":null}", CategoryPatchRequest.class));
    var productPatch = CatalogAdminCommandMapping.command(json.readValue("{\"description\":null,\"thumbnailUrl\":null}", ProductPatchRequest.class));
    var skuPatch = CatalogAdminCommandMapping.command(json.readValue("{\"compareAtPrice\":null}", SkuPatchRequest.class));
    var nextCategory = mutations.updateCategory(adminId, category.categoryId(), categoryPatch);
    var nextProduct = mutations.updateProduct(adminId, product.productId(), productPatch);
    var nextSku = mutations.updateSku(adminId, product.productId(), sku.skuId(), skuPatch);
    assertThat(nextCategory.parentId()).isNull();
    assertThat(nextCategory.name()).isEqualTo(category.name());
    assertThat(nextProduct.description()).isNull();
    assertThat(nextProduct.thumbnailUrl()).isNull();
    assertThat(nextProduct.brandId()).isEqualTo(brand.brandId());
    assertThat(nextProduct.status()).isEqualTo(product.status());
    assertThat(nextSku.compareAtPrice()).isNull();
    assertThat(nextSku.price()).isEqualTo(sku.price());
    assertThat(service.category(category.categoryId())).isEqualTo(nextCategory);
    assertThat(service.product(product.productId())).isEqualTo(nextProduct);
    assertThat(service.skus(product.productId())).containsExactly(nextSku);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE admin_id=? AND action LIKE '%UPDATE'", Integer.class, adminId)).isEqualTo(3);
    verify(cache, org.mockito.Mockito.times(3)).invalidate();
  }

  @ParameterizedTest
  @ValueSource(strings = {"BRAND_CREATE", "CATEGORY_CREATE", "CATEGORY_UPDATE", "PRODUCT_CREATE", "PRODUCT_UPDATE", "SKU_CREATE", "SKU_UPDATE"})
  void failureAfterAuditSaveAndFlushRollsBackEveryCoreMutationInventoryAndCache(String operation) {
    var before = snapshot();
    clearInvocations(cache);
    AtomicLong generated = new AtomicLong();
    doAnswer(invocation -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      assertThat((String) invocation.getArgument(1)).isEqualTo("CATALOG_" + operation);
      generated.set(invocation.getArgument(3));
      invocation.callRealMethod();
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE admin_id=? AND action=? AND target_id=?",
          Integer.class, adminId, "CATALOG_" + operation, generated.get())).isEqualTo(1);
      assertThat(snapshot()).isNotEqualTo(before);
      throw new IllegalStateException("core audit flush rollback probe");
    }).when(audits).append(anyLong(), anyString(), anyString(), anyLong());
    assertThatThrownBy(() -> {
      switch (operation) {
        case "BRAND_CREATE" -> mutations.createBrand(adminId, new BrandCreateCommand("rollback", "core-rollback", null, true, 0));
        case "CATEGORY_CREATE" -> mutations.createCategory(adminId, new CategoryCreateCommand("rollback", "core-rollback", 0, true, null));
        case "CATEGORY_UPDATE" -> mutations.updateCategory(adminId, category.categoryId(), new CategoryPatchCommand("rollback", true, null, false, null, false, null, false, null, false));
        case "PRODUCT_CREATE" -> mutations.createProduct(adminId, new ProductCreateCommand(category.categoryId(), brand.brandId(), "core-rollback", "short", null, "DOG", null));
        case "PRODUCT_UPDATE" -> mutations.updateProduct(adminId, product.productId(), new ProductPatchCommand(null, false, null, false, "rollback", true, null, false, null, false, null, false, null, false, null, false));
        case "SKU_CREATE" -> mutations.createSku(adminId, product.productId(), new SkuCreateCommand("CORE-ROLLBACK", "rollback", new BigDecimal("10.20"), null, true, 0, SkuStatus.ACTIVE));
        case "SKU_UPDATE" -> mutations.updateSku(adminId, product.productId(), sku.skuId(), new SkuPatchCommand("rollback", true, null, false, null, false, null, false, null, false, null, false));
        default -> throw new AssertionError(operation);
      }
    }).isInstanceOf(IllegalStateException.class).hasMessage("core audit flush rollback probe");
    assertThat(generated.get()).isPositive();
    assertThat(snapshot()).isEqualTo(before);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE admin_id=? AND action=? AND target_id=?",
        Integer.class, adminId, "CATALOG_" + operation, generated.get())).isZero();
    verify(cache, never()).invalidate();
  }

  private List<List<Map<String, Object>>> snapshot() {
    return List.of(
        jdbc.queryForList("SELECT * FROM brands WHERE id=? OR slug='core-rollback' ORDER BY id", brand.brandId()),
        jdbc.queryForList("SELECT * FROM categories WHERE id IN (?,?) OR slug='core-rollback' ORDER BY id", parent.categoryId(), category.categoryId()),
        jdbc.queryForList("SELECT * FROM products WHERE brand_id=? ORDER BY id", brand.brandId()),
        jdbc.queryForList("SELECT * FROM skus WHERE product_id=? ORDER BY id", product.productId()),
        jdbc.queryForList("SELECT * FROM inventories WHERE sku_id IN (SELECT id FROM skus WHERE product_id=?) ORDER BY sku_id", product.productId()));
  }
}
