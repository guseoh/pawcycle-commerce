package com.pawcycle.backend.catalog.product.application;

import com.pawcycle.backend.catalog.product.application.ProductDetailView.SkuDetail;
import com.pawcycle.backend.catalog.product.application.ProductListView.ProductSummary;
import com.pawcycle.backend.catalog.product.application.ProductListView.SkuPrice;
import com.pawcycle.backend.catalog.product.application.ProductListView.SkuPriceSummary;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.infra.SkuRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductQueryService {
	private static final List<Integer> DELIVERY_CYCLES = List.of(2, 4, 8);

	private final ProductRepository productRepository;
	private final SkuRepository skuRepository;

	public ProductQueryService(ProductRepository productRepository, SkuRepository skuRepository) {
		this.productRepository = productRepository;
		this.skuRepository = skuRepository;
	}

	@Transactional(readOnly = true)
	public ProductListView findProducts() {
		try {
			List<Product> products = productRepository.findAllPublicOrderById();
			if (products.isEmpty()) {
				return new ProductListView(List.of());
			}

			Map<Long, List<Sku>> skusByProduct = groupSkus(products);
			List<ProductSummary> summaries = products.stream()
					.map(product -> toSummary(product, skusByProduct.getOrDefault(product.getId(), List.of())))
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
			List<Sku> skus = skuRepository.findAllByProductIdOrderByDisplayOrderAscIdAsc(productId);
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

	private Map<Long, List<Sku>> groupSkus(List<Product> products) {
		List<Long> productIds = products.stream().map(Product::getId).toList();
		Map<Long, List<Sku>> skusByProduct = new LinkedHashMap<>();
		for (Sku sku : skuRepository
				.findAllByProductIdInOrderByProductIdAscDisplayOrderAscIdAsc(productIds)) {
			skusByProduct.computeIfAbsent(sku.getProduct().getId(), ignored -> new java.util.ArrayList<>())
					.add(sku);
		}
		return skusByProduct;
	}

	private ProductSummary toSummary(Product product, List<Sku> skus) {
		List<SkuPrice> prices = skus.stream()
				.map(sku -> new SkuPrice(sku.getId(), sku.getName(), sku.getPrice()))
				.toList();
		return new ProductSummary(
				product.getId(),
				product.getName(),
				product.getPetType(),
				product.getShortDescription(),
				product.getThumbnailUrl(),
				new SkuPriceSummary(prices),
				skus.stream().anyMatch(Sku::isSubscribable));
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
