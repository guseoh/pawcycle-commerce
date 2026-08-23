package com.pawcycle.backend.catalog.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.catalog.admin.api.AdminCatalogRequests.ProductCreate;
import com.pawcycle.backend.catalog.admin.api.AdminCatalogRequests.SkuCreate;
import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.infra.CategoryRepository;
import com.pawcycle.backend.catalog.product.application.ProductListCache;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

@SpringJUnitConfig(AdminCatalogCacheInvalidationIntegrationTests.Config.class)
class AdminCatalogCacheInvalidationIntegrationTests {
	@Autowired private AdminCatalogService service;
	@Autowired private CategoryRepository categoryRepository;
	@Autowired private ProductRepository productRepository;
	@Autowired private SkuRepository skuRepository;
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private ProductListCache productListCache;
	@Autowired private RecordingTransactionManager transactionManager;
	@Autowired private RollbackProbe rollbackProbe;

	@BeforeEach
	void resetState() {
		reset(categoryRepository, productRepository, skuRepository, jdbcTemplate, productListCache);
		transactionManager.resetState();
	}

	@Test
	void productMutationInvalidatesOnlyAfterSpringTransactionCommit() {
		Category category = mock(Category.class);
		when(category.isActive()).thenReturn(true);
		when(category.getSlug()).thenReturn("food");
		when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
		when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
		doAnswer(invocation -> {
			assertThat(transactionManager.commitCompleted()).isTrue();
			return null;
		}).when(productListCache).invalidate();

		service.createProduct(new ProductCreate(1L, "상품", "설명", null, "DOG", null));

		verify(productListCache).invalidate();
		assertThat(transactionManager.commitCompleted()).isTrue();
	}

	@Test
	void skuMutationInvalidatesOnlyAfterSpringTransactionCommit() {
		Product product = mock(Product.class);
		when(product.getId()).thenReturn(10L);
		when(productRepository.findById(10L)).thenReturn(Optional.of(product));
		when(skuRepository.saveAndFlush(any(Sku.class))).thenAnswer(invocation -> invocation.getArgument(0));
		doAnswer(invocation -> {
			assertThat(transactionManager.commitCompleted()).isTrue();
			return null;
		}).when(productListCache).invalidate();

		service.createSku(10L, new SkuCreate(
				"DOG-2KG", "2kg", new BigDecimal("19900.00"), true, 1, SkuStatus.ACTIVE));

		verify(productListCache).invalidate();
		assertThat(transactionManager.commitCompleted()).isTrue();
	}

	@Test
	void rollbackDoesNotInvalidateRegisteredCacheCallback() {
		assertThatThrownBy(rollbackProbe::invalidateThenFail)
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("rollback probe");

		verify(productListCache, never()).invalidate();
		assertThat(transactionManager.rollbackCompleted()).isTrue();
	}

	@Configuration(proxyBeanMethods = false)
	@EnableTransactionManagement(proxyTargetClass = true)
	static class Config {
		@Bean
		RecordingTransactionManager transactionManager() {
			return new RecordingTransactionManager();
		}

		@Bean CategoryRepository categoryRepository() { return mock(CategoryRepository.class); }
		@Bean ProductRepository productRepository() { return mock(ProductRepository.class); }
		@Bean SkuRepository skuRepository() { return mock(SkuRepository.class); }
		@Bean JdbcTemplate jdbcTemplate() { return mock(JdbcTemplate.class); }
		@Bean ProductListCache productListCache() { return mock(ProductListCache.class); }

		@Bean
		ProductListCacheInvalidator productListCacheInvalidator(ProductListCache productListCache) {
			return new ProductListCacheInvalidator(productListCache);
		}

		@Bean
		AdminCatalogService adminCatalogService(
				CategoryRepository categoryRepository,
				ProductRepository productRepository,
				SkuRepository skuRepository,
				JdbcTemplate jdbcTemplate,
				ProductListCacheInvalidator productListCacheInvalidator) {
			return new AdminCatalogService(
					categoryRepository,
					productRepository,
					skuRepository,
					jdbcTemplate,
					productListCacheInvalidator);
		}

		@Bean
		RollbackProbe rollbackProbe(ProductListCacheInvalidator productListCacheInvalidator) {
			return new RollbackProbe(productListCacheInvalidator);
		}
	}

	static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
		private volatile boolean commitCompleted;
		private volatile boolean rollbackCompleted;

		void resetState() {
			commitCompleted = false;
			rollbackCompleted = false;
		}

		boolean commitCompleted() {
			return commitCompleted;
		}

		boolean rollbackCompleted() {
			return rollbackCompleted;
		}

		@Override
		protected Object doGetTransaction() {
			return new Object();
		}

		@Override
		protected void doBegin(Object transaction, TransactionDefinition definition) {
			commitCompleted = false;
			rollbackCompleted = false;
		}

		@Override
		protected void doCommit(DefaultTransactionStatus status) {
			commitCompleted = true;
		}

		@Override
		protected void doRollback(DefaultTransactionStatus status) {
			rollbackCompleted = true;
		}
	}

	static class RollbackProbe {
		private final ProductListCacheInvalidator invalidator;

		RollbackProbe(ProductListCacheInvalidator invalidator) {
			this.invalidator = invalidator;
		}

		@Transactional
		public void invalidateThenFail() {
			invalidator.invalidateAfterCommit();
			throw new IllegalStateException("rollback probe");
		}
	}
}
