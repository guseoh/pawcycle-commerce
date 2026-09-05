package com.pawcycle.backend.catalog.admin.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.catalog.admin.api.ProductCreateRequest;
import com.pawcycle.backend.catalog.admin.api.ProductPatchRequest;
import com.pawcycle.backend.catalog.admin.api.SkuCreateRequest;
import com.pawcycle.backend.catalog.admin.api.SkuPatchRequest;
import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.persistence.CategoryRepository;
import com.pawcycle.backend.catalog.brand.domain.Brand;
import com.pawcycle.backend.catalog.brand.persistence.BrandRepository;
import com.pawcycle.backend.catalog.admin.persistence.CatalogFacetPersistenceAdapter;
import com.pawcycle.backend.commerce.inventory.persistence.InventoryRepository;
import com.pawcycle.backend.catalog.product.application.ProductListCacheInvalidator;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.persistence.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.catalog.sku.persistence.SkuRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class AdminCatalogCacheInvalidationTests {
  @Mock private CategoryRepository categoryRepository;
  @Mock private BrandRepository brandRepository;
  @Mock private ProductRepository productRepository;
  @Mock private SkuRepository skuRepository;
  @Mock private InventoryRepository inventoryRepository;
  @Mock private CatalogFacetPersistenceAdapter catalogFacets;
  @Mock private ProductListCacheInvalidator invalidator;

  private AdminCatalogService service;

  @BeforeEach
  void setUp() {
    service =
        new AdminCatalogService(
            categoryRepository,
            brandRepository,
            productRepository,
            skuRepository,
            inventoryRepository,
            catalogFacets,
            invalidator);
  }

  @Test
  void productCreateAndUpdateRequestAfterCommitInvalidation() {
    Category category = mock(Category.class);
    when(category.isActive()).thenReturn(true);
    when(category.getSlug()).thenReturn("food");
    when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
    Brand brand = mock(Brand.class);
    when(brand.isActive()).thenReturn(true);
    when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));
    when(productRepository.saveAndFlush(any(Product.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.createProduct(new ProductCreateRequest(1L, 1L, "상품", "설명", null, "DOG", null));

    Product product = mock(Product.class);
    when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(product));
    when(productRepository.saveAndFlush(product)).thenReturn(product);
    ProductPatchRequest patch = new ProductPatchRequest();
    patch.readName("수정 상품");
    service.updateProduct(10L, patch);

    verify(invalidator, times(2)).invalidateAfterCommit();
  }

  @Test
  void skuCreateAndUpdateRequestAfterCommitInvalidation() {
    Product product = mock(Product.class);
    when(product.getId()).thenReturn(10L);
    when(productRepository.findById(10L)).thenReturn(Optional.of(product));
    Sku savedSku = mock(Sku.class);
    when(savedSku.getId()).thenReturn(20L);
    when(savedSku.getProduct()).thenReturn(product);
    when(skuRepository.saveAndFlush(any(Sku.class)))
        .thenReturn(savedSku);

    service.createSku(
        10L,
        new SkuCreateRequest("DOG-2KG", "2kg", new BigDecimal("19900.00"), true, 1, SkuStatus.ACTIVE));

    clearInvocations(productRepository);
    Sku sku = mock(Sku.class);
    when(sku.getProduct()).thenReturn(product);
    when(skuRepository.findByIdAndProductId(20L, 10L)).thenReturn(Optional.of(sku));
    when(skuRepository.saveAndFlush(sku)).thenReturn(sku);
    SkuPatchRequest patch = new SkuPatchRequest();
    patch.readPrice(new BigDecimal("20900.00"));
    service.updateSku(10L, 20L, patch);

    verify(invalidator, times(2)).invalidateAfterCommit();
  }
}
