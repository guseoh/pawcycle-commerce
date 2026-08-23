package com.pawcycle.backend.catalog.product.application;

import com.pawcycle.backend.catalog.product.application.ProductDetailView.SkuDetail;
import com.pawcycle.backend.catalog.product.application.ProductListView.ProductSummary;
import com.pawcycle.backend.catalog.product.application.ProductListView.SkuPrice;
import com.pawcycle.backend.catalog.product.application.ProductListView.SkuPriceSummary;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.infra.SkuRepository;
import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductQueryService {
	private static final List<Integer> DELIVERY_CYCLES = List.of(2, 4, 8);

	private final ProductListReader productListReader;
	private final ProductRepository productRepository;
	private final SkuRepository skuRepository;

	public ProductQueryService(
			ProductListReader productListReader,
			ProductRepository productRepository,
			SkuRepository skuRepository) {
		this.productListReader = productListReader;
		this.productRepository = productRepository;
		this.skuRepository = skuRepository;
	}

	public ProductListView findProducts() {
		try {
			ProductListReader.ProductListSnapshot snapshot = productListReader.read();
			if (snapshot.products().isEmpty()) {
				return new ProductListView(List.of());
			}

			Map<Long, List<ProductListReader.SkuSnapshot>> skusByProduct = groupSkus(snapshot.skus());
			List<ProductSummary> summaries = snapshot.products().stream()
					.map(product -> toSummary(product, skusByProduct.getOrDefault(product.productId(), List.of())))
					.toList();
			return new ProductListView(summaries);
		} catch (RuntimeException exception) {
			throw new ProductListUnavailableException(exception);
		}
	}

	@Transactional(readOnly = true)
	public ProductDetailView findProduct(Long productId) {
		Product product;
		try {
			product = productRepository.findPublicById(productId).orElseThrow(ProductNotFoundException::new);
			List<Sku> skus = skuRepository.findAllByProductIdAndStatusOrderByDisplayOrderAscIdAsc(
					productId,
					SkuStatus.ACTIVE);
			return new ProductDetailView(
					product.getId(),
					product.getName(),
					product.getPetType(),
					product.getDescription(),
					product.getThumbnailUrl(),
					skus.stream().map(this::toDetail).toList());
		} catch (ProductNotFoundException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new ProductDetailUnavailableException(exception);
		}
	}

	private Map<Long, List<ProductListReader.SkuSnapshot>> groupSkus(
			List<ProductListReader.SkuSnapshot> skus) {
		Map<Long, List<ProductListReader.SkuSnapshot>> skusByProduct = new LinkedHashMap<>();
		for (ProductListReader.SkuSnapshot sku : skus) {
			skusByProduct.computeIfAbsent(sku.productId(), ignored -> new java.util.ArrayList<>())
					.add(sku);
		}
		return skusByProduct;
	}

	private ProductSummary toSummary(
			ProductListReader.ProductSnapshot product,
			List<ProductListReader.SkuSnapshot> skus) {
		List<SkuPrice> prices = skus.stream()
				.map(sku -> new SkuPrice(sku.skuId(), sku.skuName(), sku.price()))
				.toList();
		return new ProductSummary(
				product.productId(),
				product.name(),
				product.petType(),
				product.shortDescription(),
				product.thumbnailUrl(),
				new SkuPriceSummary(prices),
				skus.stream().anyMatch(ProductListReader.SkuSnapshot::subscribable));
	}

	private SkuDetail toDetail(Sku sku) {
		return new SkuDetail(
				sku.getId(),
				sku.getName(),
				sku.getPrice(),
				sku.isSubscribable(),
				sku.isSubscribable() ? DELIVERY_CYCLES : List.of());
	}
}
