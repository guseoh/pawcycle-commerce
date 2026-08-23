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
import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.catalog.sku.infra.SkuRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class ProductListReaderTests {

	@Mock
	private ProductRepository productRepository;

	@Mock
	private SkuRepository skuRepository;

	@Test
	void materializesProductAndSkuFieldsWithoutReturningEntities() {
		Product product = mock(Product.class);
		Sku sku = mock(Sku.class);
		when(product.getId()).thenReturn(1L);
		when(product.getName()).thenReturn("상품");
		when(product.getPetType()).thenReturn("DOG");
		when(product.getShortDescription()).thenReturn("짧은 설명");
		when(product.getThumbnailUrl()).thenReturn("thumbnail");
		when(sku.getProduct()).thenReturn(product);
		when(sku.getId()).thenReturn(10L);
		when(sku.getName()).thenReturn("2kg");
		when(sku.getPrice()).thenReturn(new BigDecimal("19900.00"));
		when(sku.isSubscribable()).thenReturn(true);
		when(productRepository.findAllPublicOrderById()).thenReturn(List.of(product));
		when(skuRepository.findAllByProductIdInAndStatusOrderByProductIdAscDisplayOrderAscIdAsc(
				List.of(1L), SkuStatus.ACTIVE)).thenReturn(List.of(sku));

		ProductListReader.ProductListSnapshot snapshot = new ProductListReader(productRepository, skuRepository).read();

		assertThat(snapshot.products()).extracting(ProductListReader.ProductSnapshot::productId).containsExactly(1L);
		assertThat(snapshot.skus()).extracting(ProductListReader.SkuSnapshot::skuId).containsExactly(10L);
		assertThat(snapshot.skus().get(0).price()).isEqualByComparingTo("19900.00");
		assertThatThrownBy(() -> snapshot.products().add(null)).isInstanceOf(UnsupportedOperationException.class);
		verify(productRepository).findAllPublicOrderById();
		verify(skuRepository).findAllByProductIdInAndStatusOrderByProductIdAscDisplayOrderAscIdAsc(
				List.of(1L), SkuStatus.ACTIVE);
	}

	@Test
	void emptyProductsAvoidSecondQuery() {
		when(productRepository.findAllPublicOrderById()).thenReturn(List.of());

		ProductListReader.ProductListSnapshot snapshot = new ProductListReader(productRepository, skuRepository).read();

		assertThat(snapshot.products()).isEmpty();
		assertThat(snapshot.skus()).isEmpty();
		verifyNoInteractions(skuRepository);
	}

	@Test
	void readUsesReadOnlyTransaction() throws NoSuchMethodException {
		Transactional transaction = ProductListReader.class.getMethod("read").getAnnotation(Transactional.class);

		assertThat(transaction).isNotNull();
		assertThat(transaction.readOnly()).isTrue();
	}
}
