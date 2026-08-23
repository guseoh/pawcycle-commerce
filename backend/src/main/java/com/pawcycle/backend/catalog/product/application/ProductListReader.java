package com.pawcycle.backend.catalog.product.application;

import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.catalog.sku.infra.SkuRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductListReader {

	private final ProductRepository productRepository;
	private final SkuRepository skuRepository;

	public ProductListReader(ProductRepository productRepository, SkuRepository skuRepository) {
		this.productRepository = productRepository;
		this.skuRepository = skuRepository;
	}

	@Transactional(readOnly = true)
	public ProductListSnapshot read() {
		List<Product> products = productRepository.findAllPublicOrderById();
		if (products.isEmpty()) {
			return new ProductListSnapshot(List.of(), List.of());
		}

		List<ProductSnapshot> productSnapshots = products.stream()
				.map(product -> new ProductSnapshot(
						product.getId(),
						product.getName(),
						product.getPetType(),
						product.getShortDescription(),
						product.getThumbnailUrl()))
				.toList();
		List<Long> productIds = products.stream().map(Product::getId).toList();
		List<SkuSnapshot> skuSnapshots = skuRepository
				.findAllByProductIdInAndStatusOrderByProductIdAscDisplayOrderAscIdAsc(productIds, SkuStatus.ACTIVE)
				.stream()
				.map(sku -> new SkuSnapshot(
						sku.getProduct().getId(),
						sku.getId(),
						sku.getName(),
						sku.getPrice(),
						sku.isSubscribable()))
				.toList();
		return new ProductListSnapshot(productSnapshots, skuSnapshots);
	}

	public record ProductListSnapshot(List<ProductSnapshot> products, List<SkuSnapshot> skus) {
		public ProductListSnapshot {
			products = List.copyOf(products);
			skus = List.copyOf(skus);
		}
	}

	public record ProductSnapshot(
			Long productId,
			String name,
			String petType,
			String shortDescription,
			String thumbnailUrl) {
	}

	public record SkuSnapshot(
			Long productId,
			Long skuId,
			String skuName,
			BigDecimal price,
			boolean subscribable) {
	}
}
