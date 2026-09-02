package com.pawcycle.backend.catalog.product.application;

import com.pawcycle.backend.catalog.product.application.ProductDetailView.SkuDetail;
import com.pawcycle.backend.catalog.product.application.ProductDiscoveryReader.ProductDetailSkuRow;
import com.pawcycle.backend.catalog.product.application.ProductListView.CategorySummary;
import com.pawcycle.backend.catalog.product.application.ProductListView.ProductSummary;
import com.pawcycle.backend.catalog.product.application.ProductListView.SkuPrice;
import com.pawcycle.backend.catalog.product.application.ProductListView.SkuPriceSummary;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.catalog.sku.infra.SkuRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductQueryService {
  private static final List<Integer> DELIVERY_CYCLES = List.of(2, 4, 8);

  private final ProductListCache productListCache;
  private final ProductListReader productListReader;
  private final ProductRepository productRepository;
  private final SkuRepository skuRepository;
  private final ProductDiscoveryReader productDiscoveryReader;
  private final ProductDetailContentReader productDetailContentReader;

  public ProductQueryService(
      ProductListCache productListCache,
      ProductListReader productListReader,
      ProductRepository productRepository,
      SkuRepository skuRepository) {
    this.productListCache = productListCache;
    this.productListReader = productListReader;
    this.productRepository = productRepository;
    this.skuRepository = skuRepository;
    this.productDiscoveryReader = null;
    this.productDetailContentReader = null;
  }

  @Autowired
  public ProductQueryService(
      ProductListCache productListCache,
      ProductListReader productListReader,
      ProductRepository productRepository,
      SkuRepository skuRepository,
      ProductDiscoveryReader productDiscoveryReader,
      ProductDetailContentReader productDetailContentReader) {
    this.productListCache = productListCache;
    this.productListReader = productListReader;
    this.productRepository = productRepository;
    this.skuRepository = skuRepository;
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
      java.math.BigDecimal minPrice,
      java.math.BigDecimal maxPrice,
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
    if (productDiscoveryReader != null) {
      return findProducts(q, petType, category, 0, 20, ProductSort.NEWEST);
    }
    try {
      ProductListView allProducts = productListCache.getOrLoad(this::loadProducts);
      if ((q == null || q.isBlank())
          && (petType == null || petType.isBlank())
          && (category == null || category.isBlank())) {
        return allProducts;
      }
      return new ProductListView(
          allProducts.products().stream()
              .filter(product -> matches(product, q, petType, category))
              .toList());
    } catch (RuntimeException exception) {
      throw new ProductListUnavailableException(exception);
    }
  }

  public ProductListView findProducts() {
    return findProducts(null, null, null);
  }

  private ProductListView loadProducts() {
    ProductListReader.ProductListSnapshot snapshot = productListReader.read();
    if (snapshot.products().isEmpty()) {
      return new ProductListView(List.of());
    }

    Map<Long, List<ProductListReader.SkuSnapshot>> skusByProduct = groupSkus(snapshot.skus());
    List<ProductSummary> summaries =
        snapshot.products().stream()
            .map(
                product ->
                    toSummary(product, skusByProduct.getOrDefault(product.productId(), List.of())))
            .toList();
    return new ProductListView(summaries);
  }

  @Transactional(readOnly = true)
  public ProductDetailView findProduct(Long productId) {
    Product product;
    try {
      product =
          productRepository.findPublicById(productId).orElseThrow(ProductNotFoundException::new);
      List<SkuDetail> skuDetails =
          productDiscoveryReader == null
              ? skuRepository
                  .findAllByProductIdAndStatusOrderByDisplayOrderAscIdAsc(
                      productId, SkuStatus.ACTIVE)
                  .stream()
                  .map(this::toDetail)
                  .toList()
              : productDiscoveryReader.readDetailSkus(productId).stream()
                  .map(this::toDetail)
                  .toList();
      ProductDiscoveryReader.ProductDetailSupplement supplement =
          productDiscoveryReader == null
              ? ProductDiscoveryReader.ProductDetailSupplement.empty()
              : productDiscoveryReader.readDetailSupplement(productId);
      if (productDiscoveryReader != null && supplement.brand() == null)
        throw new ProductNotFoundException();
      return new ProductDetailView(
          product.getId(),
          product.getName(),
          product.getShortDescription(),
          product.getPetType(),
          product.getDescription(),
          product.getThumbnailUrl(),
          new ProductDetailView.CategorySummary(
              product.getCategory().getId(),
              product.getCategory().getName(),
              product.getCategory().getSlug()),
          productDetailContentReader == null
              ? List.of()
              : productDetailContentReader.visibleSections(productId),
          productDetailContentReader == null
              ? ProductDetailView.Trust.empty()
              : toTrust(productDetailContentReader.trust(productId)),
          skuDetails,
          skuDetails.stream().anyMatch(ProductDetailView.SkuDetail::purchasable),
          supplement.brand(),
          supplement.images(),
          supplement.optionGroups());
    } catch (ProductNotFoundException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new ProductDetailUnavailableException(exception);
    }
  }

  private ProductDetailView.Trust toTrust(ProductDetailContentReader.ProductTrustView trust) {
    return new ProductDetailView.Trust(
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

  private Map<Long, List<ProductListReader.SkuSnapshot>> groupSkus(
      List<ProductListReader.SkuSnapshot> skus) {
    Map<Long, List<ProductListReader.SkuSnapshot>> skusByProduct = new LinkedHashMap<>();
    for (ProductListReader.SkuSnapshot sku : skus) {
      skusByProduct
          .computeIfAbsent(sku.productId(), ignored -> new java.util.ArrayList<>())
          .add(sku);
    }
    return skusByProduct;
  }

  private ProductSummary toSummary(
      ProductListReader.ProductSnapshot product, List<ProductListReader.SkuSnapshot> skus) {
    List<SkuPrice> prices =
        skus.stream().map(sku -> new SkuPrice(sku.skuId(), sku.skuName(), sku.price())).toList();
    return new ProductSummary(
        product.productId(),
        product.name(),
        product.petType(),
        product.shortDescription(),
        product.thumbnailUrl(),
        new CategorySummary(
            product.category().categoryId(), product.category().name(), product.category().slug()),
        new SkuPriceSummary(prices),
        skus.stream().anyMatch(ProductListReader.SkuSnapshot::subscribable));
  }

  private boolean matches(ProductSummary product, String q, String petType, String category) {
    return matchesQuery(product, q)
        && (petType == null
            || petType.isBlank()
            || product.petType().equalsIgnoreCase(petType.trim()))
        && (category == null
            || category.isBlank()
            || product.category().slug().equalsIgnoreCase(category.trim()));
  }

  private boolean matchesQuery(ProductSummary product, String q) {
    if (q == null || q.isBlank()) return true;
    String needle = q.trim().toLowerCase(java.util.Locale.ROOT);
    return product.name().toLowerCase(java.util.Locale.ROOT).contains(needle)
        || product.shortDescription().toLowerCase(java.util.Locale.ROOT).contains(needle);
  }

  private SkuDetail toDetail(Sku sku) {
    return new SkuDetail(
        sku.getId(),
        sku.getName(),
        sku.getPrice(),
        sku.isSubscribable(),
        sku.isSubscribable() ? DELIVERY_CYCLES : List.of(),
        1,
        true);
  }

  private SkuDetail toDetail(ProductDetailSkuRow sku) {
    return new SkuDetail(
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

  private Integer discountRate(java.math.BigDecimal price, java.math.BigDecimal compareAtPrice) {
    if (compareAtPrice == null || price == null || compareAtPrice.signum() <= 0) return null;
    return compareAtPrice
        .subtract(price)
        .multiply(java.math.BigDecimal.valueOf(100))
        .divide(compareAtPrice, 0, java.math.RoundingMode.DOWN)
        .intValue();
  }
}
