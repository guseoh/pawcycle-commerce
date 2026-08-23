package com.pawcycle.backend.catalog.admin.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.catalog.admin.api.AdminCatalogRequests.ProductCreate;
import com.pawcycle.backend.catalog.admin.api.AdminCatalogRequests.ProductPatch;
import com.pawcycle.backend.catalog.admin.api.AdminCatalogRequests.SkuCreate;
import com.pawcycle.backend.catalog.admin.api.AdminCatalogRequests.SkuPatch;
import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.infra.CategoryRepository;
import com.pawcycle.backend.catalog.product.application.ProductListCacheInvalidator;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.catalog.sku.infra.SkuRepository;
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
	@Mock private ProductRepository productRepository;
	@Mock private SkuRepository skuRepository;
	@Mock private JdbcTemplate jdbcTemplate;
	@Mock private ProductListCacheInvalidator invalidator;

	private AdminCatalogService service;

	@BeforeEach
	void setUp() {
		service = new AdminCatalogService(
				categoryRepository, productRepository, skuRepository, jdbcTemplate, invalidator);
	}

	@Test
	void productCreateAndUpdateRequestAfterCommitInvalidation() {
		Category category = mock(Category.class);
		when(category.isActive()).thenReturn(true);
		when(category.getSlug()).thenReturn("food");
		when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
		when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service.createProduct(new ProductCreate(1L, "상품", "설명", null, "DOG", null));

		Product product = mock(Product.class);
		when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(product));
		when(productRepository.saveAndFlush(product)).thenReturn(product);
		ProductPatch patch = new ProductPatch();
		patch.readName("수정 상품");
		service.updateProduct(10L, patch);

		verify(invalidator, times(2)).invalidateAfterCommit();
	}

	@Test
	void skuCreateAndUpdateRequestAfterCommitInvalidation() {
		Product product = mock(Product.class);
		when(product.getId()).thenReturn(10L);
		when(productRepository.findById(10L)).thenReturn(Optional.of(product));
		when(skuRepository.saveAndFlush(any(Sku.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service.createSku(10L, new SkuCreate(
				"DOG-2KG", "2kg", new BigDecimal("19900.00"), true, 1, SkuStatus.ACTIVE));

		clearInvocations(productRepository);
		Sku sku = mock(Sku.class);
		when(sku.getProduct()).thenReturn(product);
		when(skuRepository.findByIdAndProductId(20L, 10L)).thenReturn(Optional.of(sku));
		when(skuRepository.saveAndFlush(sku)).thenReturn(sku);
		SkuPatch patch = new SkuPatch();
		patch.readPrice(new BigDecimal("20900.00"));
		service.updateSku(10L, 20L, patch);

		verify(invalidator, times(2)).invalidateAfterCommit();
	}
}
