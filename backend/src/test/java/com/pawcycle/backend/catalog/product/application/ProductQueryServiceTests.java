package com.pawcycle.backend.catalog.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.infra.SkuRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductQueryServiceTests {

	@Mock
	private ProductRepository productRepository;

	@Mock
	private SkuRepository skuRepository;

	private ProductQueryService productQueryService;

	@BeforeEach
	void setUp() {
		productQueryService = new ProductQueryService(productRepository, skuRepository);
	}

	@Test
	void emptyListSkipsSkuQuery() {
		when(productRepository.findAllPublicOrderById()).thenReturn(List.of());

		ProductListView response = productQueryService.findProducts();

		assertThat(response.products()).isEmpty();
		verifyNoInteractions(skuRepository);
	}

	@Test
	void listUsesOneProductQueryAndOneBatchSkuQuery() {
		Product first = product(1L, "첫 상품", "DOG", "첫 설명", null, null);
		Product second = product(2L, "둘째 상품", "CAT", "둘째 설명", null, null);
		Sku firstSku = sku(10L, first, "2kg", "19900.00", true);
		Sku secondSku = sku(11L, first, "5kg", "39900.00", false);
		when(productRepository.findAllPublicOrderById()).thenReturn(List.of(first, second));
		when(skuRepository.findAllByProductIdInOrderByProductIdAscDisplayOrderAscIdAsc(List.of(1L, 2L)))
				.thenReturn(List.of(firstSku, secondSku));

		ProductListView response = productQueryService.findProducts();

		assertThat(response.products()).hasSize(2);
		assertThat(response.products().get(0).skuPriceSummary().skuPrices())
				.extracting(ProductListView.SkuPrice::skuId)
				.containsExactly(10L, 11L);
		assertThat(response.products().get(0).hasSubscribableSku()).isTrue();
		assertThat(response.products().get(1).skuPriceSummary().skuPrices()).isEmpty();
		assertThat(response.products().get(1).hasSubscribableSku()).isFalse();
		verify(productRepository).findAllPublicOrderById();
		verify(skuRepository).findAllByProductIdInOrderByProductIdAscDisplayOrderAscIdAsc(List.of(1L, 2L));
	}

	@Test
	void detailMapsDeliveryCyclesBySubscribableValue() {
		Product product = product(1L, "상품", "DOG", "짧은 설명", null, null);
		Sku subscribable = sku(10L, product, "2kg", "19900.00", true);
		Sku notSubscribable = sku(11L, product, "5kg", "39900.00", false);
		when(productRepository.findPublicById(1L)).thenReturn(Optional.of(product));
		when(skuRepository.findAllByProductIdOrderByDisplayOrderAscIdAsc(1L))
				.thenReturn(List.of(subscribable, notSubscribable));

		ProductDetailView response = productQueryService.findProduct(1L);

		assertThat(response.skus().get(0).availableDeliveryCycles()).containsExactly(2, 4, 8);
		assertThat(response.skus().get(1).availableDeliveryCycles()).isEmpty();
	}

	@Test
	void missingOrNonPublicProductUsesNotFoundException() {
		when(productRepository.findPublicById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> productQueryService.findProduct(99L))
				.isInstanceOf(ProductNotFoundException.class)
				.hasMessage("상품을 확인할 수 없습니다.");
		verifyNoInteractions(skuRepository);
	}

	@Test
	void unexpectedRepositoryFailuresUseEndpointSpecificExceptions() {
		when(productRepository.findAllPublicOrderById()).thenThrow(new IllegalStateException("table details"));
		when(productRepository.findPublicById(1L)).thenThrow(new IllegalStateException("column details"));

		assertThatThrownBy(productQueryService::findProducts)
				.isInstanceOf(ProductListUnavailableException.class);
		assertThatThrownBy(() -> productQueryService.findProduct(1L))
				.isInstanceOf(ProductDetailUnavailableException.class);
	}

	@Test
	void queryMethodsUseReadOnlyTransactions() throws NoSuchMethodException {
		Transactional listTransaction = ProductQueryService.class
				.getMethod("findProducts")
				.getAnnotation(Transactional.class);
		Transactional detailTransaction = ProductQueryService.class
				.getMethod("findProduct", Long.class)
				.getAnnotation(Transactional.class);

		assertThat(listTransaction.readOnly()).isTrue();
		assertThat(detailTransaction.readOnly()).isTrue();
	}

	private Product product(
			Long id,
			String name,
			String petType,
			String shortDescription,
			String description,
			String thumbnailUrl) {
		Product product = mock(Product.class);
		when(product.getId()).thenReturn(id);
		when(product.getName()).thenReturn(name);
		when(product.getPetType()).thenReturn(petType);
		when(product.getShortDescription()).thenReturn(shortDescription);
		when(product.getDescription()).thenReturn(description);
		when(product.getThumbnailUrl()).thenReturn(thumbnailUrl);
		return product;
	}

	private Sku sku(Long id, Product product, String name, String price, boolean subscribable) {
		Sku sku = mock(Sku.class);
		when(sku.getId()).thenReturn(id);
		when(sku.getProduct()).thenReturn(product);
		when(sku.getName()).thenReturn(name);
		when(sku.getPrice()).thenReturn(new BigDecimal(price));
		when(sku.isSubscribable()).thenReturn(subscribable);
		return sku;
	}
}
