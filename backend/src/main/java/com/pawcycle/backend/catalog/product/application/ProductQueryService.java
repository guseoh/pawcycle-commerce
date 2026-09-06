package com.pawcycle.backend.catalog.product.application;

import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.persistence.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductQueryService {
  private static final List<Integer> DELIVERY_CYCLES = List.of(2, 4, 8);

  private final ProductRepository productRepository;
  private final ProductDiscoveryReader productDiscoveryReader;
  private final ProductDetailContentReader productDetailContentReader;

  public ProductQueryService(
      ProductRepository productRepository,
      ProductDiscoveryReader productDiscoveryReader,
      ProductDetailContentReader productDetailContentReader) {
    this.productRepository = productRepository;
    this.productDiscoveryReader = productDiscoveryReader;
    this.productDetailContentReader = productDetailContentReader;
  }

  public ProductListView findProducts(
      String q, String petType, String category, int page, int size, ProductSort sort) {
    return findProducts(
        q, petType, category, null, null, List.of(), null, null, null, null, page, size, sort);
  }

  public ProductListView findProducts(
      String q,
      String petType,
      String category,
      String subcategory,
      String brand,
      List<String> facets,
      BigDecimal minPrice,
      BigDecimal maxPrice,
      Boolean subscribable,
      Boolean purchasable,
      int page,
      int size,
      ProductSort sort) {
    if (page < 0 || size < 1 || size > 100) {
      throw new IllegalArgumentException("page는 0 이상, size는 1~100이어야 합니다.");
    }
    try {
      Math.multiplyExact(page, size);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("page가 너무 큽니다.", exception);
    }
    if ((minPrice != null && minPrice.signum() < 0)
        || (maxPrice != null && maxPrice.signum() < 0)
        || (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)) {
      throw new IllegalArgumentException("가격 범위가 올바르지 않습니다.");
    }
    validateFacets(facets);
    try {
      return productDiscoveryReader.read(
          q,
          petType,
          category,
          subcategory,
          brand,
          facets,
          minPrice,
          maxPrice,
          subscribable,
          purchasable,
          page,
          size,
          sort == null ? ProductSort.NEWEST : sort);
    } catch (RuntimeException exception) {
      throw new ProductListUnavailableException(exception);
    }
  }

  public ProductListView findProducts(String q, String petType, String category) {
    return findProducts(q, petType, category, 0, 20, ProductSort.NEWEST);
  }

  public ProductListView findProducts() {
    return findProducts(null, null, null);
  }

  @Transactional(readOnly = true)
  public ProductDetailView findProduct(Long productId) {
    try {
      Product product =
          productRepository.findPublicById(productId).orElseThrow(ProductNotFoundException::new);
      List<ProductSkuDetail> skuDetails =
          productDiscoveryReader.readDetailSkus(productId).stream()
              .map(this::toDetail)
              .toList();
      ProductDetailSupplement supplement =
          productDiscoveryReader.readDetailSupplement(productId);
      if (supplement.brand() == null) throw new ProductNotFoundException();
      return new ProductDetailView(
          product.getId(),
          product.getName(),
          product.getShortDescription(),
          product.getPetType(),
          product.getDescription(),
          product.getThumbnailUrl(),
          new CategorySummary(
              product.getCategory().getId(),
              product.getCategory().getName(),
              product.getCategory().getSlug()),
          productDetailContentReader.visibleSections(productId),
          toTrust(productDetailContentReader.trust(productId)),
          skuDetails,
          skuDetails.stream().anyMatch(ProductSkuDetail::purchasable),
          supplement.brand(),
          supplement.images(),
          supplement.optionGroups());
    } catch (ProductNotFoundException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new ProductDetailUnavailableException(exception);
    }
  }

  private ProductTrust toTrust(ProductTrustProjection trust) {
    return new ProductTrust(
        trust.averageRating(), trust.reviewCount(), trust.questionCount());
  }

  private void validateFacets(List<String> facets) {
    for (String facet : facets == null ? List.<String>of() : facets) {
      String[] pair = facet == null ? new String[0] : facet.split(":", 2);
      if (pair.length != 2 || pair[0].isBlank() || pair[1].isBlank()) {
        throw new IllegalArgumentException("facet은 key:value 형식이어야 합니다.");
      }
    }
  }

  private ProductSkuDetail toDetail(ProductDetailSkuRow sku) {
    return new ProductSkuDetail(
        sku.skuId(),
        sku.skuName(),
        sku.price(),
        sku.subscribable(),
        sku.subscribable() ? DELIVERY_CYCLES : List.of(),
        sku.availableQuantity(),
        sku.availableQuantity() > 0,
        sku.compareAtPrice(),
        discountRate(sku.price(), sku.compareAtPrice()),
        sku.selectedOptions());
  }

  private Integer discountRate(BigDecimal price, BigDecimal compareAtPrice) {
    if (compareAtPrice == null || price == null || compareAtPrice.signum() <= 0) return null;
    return compareAtPrice
        .subtract(price)
        .multiply(BigDecimal.valueOf(100))
        .divide(compareAtPrice, 0, java.math.RoundingMode.DOWN)
        .intValue();
  }
}
