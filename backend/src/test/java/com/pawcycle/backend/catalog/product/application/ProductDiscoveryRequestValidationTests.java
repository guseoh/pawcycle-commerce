package com.pawcycle.backend.catalog.product.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import com.pawcycle.backend.catalog.sku.infra.SkuRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductDiscoveryRequestValidationTests {

	@Test
	void malformedFacetIsRejectedAsRequestValidationBeforeDiscoveryFailureWrapping() {
		ProductListCache productListCache = mock(ProductListCache.class);
		ProductListReader productListReader = mock(ProductListReader.class);
		ProductRepository productRepository = mock(ProductRepository.class);
		SkuRepository skuRepository = mock(SkuRepository.class);
		ProductDiscoveryReader discoveryReader = mock(ProductDiscoveryReader.class);
		ProductDetailContentReader detailContentReader = mock(ProductDetailContentReader.class);
		ProductQueryService service = new ProductQueryService(
				productListCache,
				productListReader,
				productRepository,
				skuRepository,
				discoveryReader,
				detailContentReader);

		assertThatThrownBy(() -> service.findProducts(
				null, null, null, null, null, List.of("broken-facet"),
				null, null, null, null, 0, 20, ProductSort.NEWEST))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("facet은 key:value 형식이어야 합니다.");

		verifyNoInteractions(discoveryReader);
	}
}
