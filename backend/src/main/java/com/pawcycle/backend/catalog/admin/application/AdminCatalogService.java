package com.pawcycle.backend.catalog.admin.application;
import com.pawcycle.backend.catalog.admin.api.BrandCreateRequest;
import com.pawcycle.backend.catalog.admin.api.CategoryListResponse;
import com.pawcycle.backend.catalog.admin.api.CategoryResponse;
import com.pawcycle.backend.catalog.admin.api.BrandListResponse;
import com.pawcycle.backend.catalog.admin.api.BrandResponse;
import com.pawcycle.backend.catalog.admin.api.ProductListResponse;
import com.pawcycle.backend.catalog.admin.api.ProductResponse;
import com.pawcycle.backend.catalog.admin.api.SkuListResponse;
import com.pawcycle.backend.catalog.admin.api.SkuResponse;

import com.pawcycle.backend.catalog.admin.api.CategoryCreateRequest;
import com.pawcycle.backend.catalog.admin.api.CategoryPatchRequest;
import com.pawcycle.backend.catalog.admin.api.ProductCreateRequest;
import com.pawcycle.backend.catalog.admin.api.ProductPatchRequest;
import com.pawcycle.backend.catalog.admin.api.SkuCreateRequest;
import com.pawcycle.backend.catalog.admin.api.SkuPatchRequest;
import com.pawcycle.backend.catalog.brand.domain.Brand;
import com.pawcycle.backend.catalog.brand.persistence.BrandRepository;
import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.persistence.CategoryRepository;
import com.pawcycle.backend.catalog.product.application.ProductListCacheInvalidator;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.persistence.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.persistence.SkuRepository;
import com.pawcycle.backend.common.error.FieldErrorResponse;
import com.pawcycle.backend.catalog.admin.persistence.CatalogFacetPersistenceAdapter;
import com.pawcycle.backend.commerce.inventory.persistence.InventoryEntity;
import com.pawcycle.backend.commerce.inventory.persistence.InventoryRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminCatalogService {
  private static final Pattern SLUG_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
  private static final String SYSTEM_UNCATEGORIZED_SLUG = "__pawcycle_uncategorized__";

  private final CategoryRepository categoryRepository;
  private final BrandRepository brandRepository;
  private final ProductRepository productRepository;
  private final SkuRepository skuRepository;
  private final InventoryRepository inventoryRepository;
  private final CatalogFacetPersistenceAdapter catalogFacets;
  private final ProductListCacheInvalidator productListCacheInvalidator;

  @Transactional(readOnly = true)
  public BrandListResponse brands() {
    return new BrandListResponse(
        brandRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
            .map(this::brandView)
            .toList());
  }

  @Transactional
  public BrandResponse createBrand(
      com.pawcycle.backend.catalog.admin.api.BrandCreateRequest request) {
    if (brandRepository.existsBySlug(request.slug())) {
      throw new AdminCatalogConflictException("BRAND_SLUG_CONFLICT", "이미 사용 중인 브랜드 slug입니다.");
    }
    try {
      return brandView(
          brandRepository.saveAndFlush(
              new Brand(
                  request.name(),
                  request.slug(),
                  request.logoUrl(),
                  request.active(),
                  request.displayOrder())));
    } catch (DataIntegrityViolationException exception) {
      throw new AdminCatalogConflictException("BRAND_SLUG_CONFLICT", "이미 사용 중인 브랜드 slug입니다.");
    }
  }

  @Transactional(readOnly = true)
  public CategoryListResponse categories() {
    return new CategoryListResponse(
        categoryRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
            .map(this::categoryView)
            .toList());
  }

  @Transactional(readOnly = true)
  public CategoryResponse category(Long categoryId) {
    return categoryView(requireCategory(categoryId));
  }

  @Transactional
  public CategoryResponse createCategory(CategoryCreateRequest request) {
    if (categoryRepository.existsBySlug(request.slug())) {
      throw slugConflict();
    }
    try {
      Category category =
          categoryRepository.saveAndFlush(
              new Category(
                  request.name(), request.slug(), request.displayOrder(), request.active()));
      if (request.parentId() != null)
        category.updateParent(requireParentCategory(request.parentId(), null));
      productListCacheInvalidator.invalidateAfterCommit();
      return categoryView(category);
    } catch (DataIntegrityViolationException exception) {
      throw slugConflict();
    }
  }

  @Transactional
  public CategoryResponse updateCategory(Long categoryId, CategoryPatchRequest request) {
    validate(request);
    Category category = requireCategory(categoryId);
    if (request.isParentIdPresent()) {
      category.updateParent(
          request.getParentId() == null
              ? null
              : requireParentCategory(request.getParentId(), categoryId));
    }
    if (request.isSlugPresent()
        && categoryRepository.existsBySlugAndIdNot(request.getSlug(), categoryId)) {
      throw slugConflict();
    }
    category.update(
        request.getName(), request.getSlug(), request.getDisplayOrder(), request.getActive());
    try {
      CategoryResponse view = categoryView(categoryRepository.saveAndFlush(category));
      productListCacheInvalidator.invalidateAfterCommit();
      return view;
    } catch (DataIntegrityViolationException exception) {
      throw slugConflict();
    }
  }

  @Transactional(readOnly = true)
  public ProductListResponse products() {
    return new ProductListResponse(
        productRepository.findAllWithCategoryOrderByIdAsc().stream()
            .map(this::productView)
            .toList());
  }

  @Transactional(readOnly = true)
  public ProductResponse product(Long productId) {
    return productView(requireProduct(productId));
  }

  @Transactional
  public ProductResponse createProduct(ProductCreateRequest request) {
    Category category = requireAssignableCategory(request.categoryId());
    if (request.brandId() == null) {
      throw new AdminCatalogValidationException(List.of(error("brandId", "필수 입력입니다.")));
    }
    long brandId = request.brandId();
    requireActiveBrand(brandId);
    Product product =
        productRepository.saveAndFlush(
            new Product(
                category,
                request.name(),
                request.shortDescription(),
                request.description(),
                request.petType(),
                request.thumbnailUrl()));
    product.updateBrandId(brandId);
    productListCacheInvalidator.invalidateAfterCommit();
    return productView(product);
  }

  @Transactional
  public ProductResponse updateProduct(Long productId, ProductPatchRequest request) {
    validate(request);
    Product product = requireProductForUpdate(productId);
    Category category =
        request.isCategoryIdPresent() && request.getCategoryId() != null
            ? requireAssignableCategory(request.getCategoryId())
            : null;
    if (request.isCategoryIdPresent()
        && category != null
        && (product.getCategory() == null
            || !category.getId().equals(product.getCategory().getId()))) {
      requireFacetValuesCompatibleWithCategory(productId, category.getId());
    }
    if (request.isBrandIdPresent()) {
      if (request.getBrandId() == null) {
        throw new AdminCatalogValidationException(
            List.of(error("brandId", "Brand cannot be cleared.")));
      }
      requireActiveBrand(request.getBrandId());
      product.updateBrandId(request.getBrandId());
    }
    product.update(
        category,
        request.isCategoryIdPresent(),
        request.getName(),
        request.getShortDescription(),
        request.getDescription(),
        request.isDescriptionPresent(),
        request.getPetType(),
        request.getThumbnailUrl(),
        request.isThumbnailUrlPresent());
    if (request.isStatusPresent()) {
      if (!product.canTransitionTo(request.getStatus())) {
        throw new AdminCatalogConflictException(
            "PRODUCT_STATUS_TRANSITION_CONFLICT", "허용되지 않은 상품 상태 전이입니다.");
      }
      product.transitionTo(request.getStatus());
    }
    ProductResponse view = productView(productRepository.saveAndFlush(product));
    productListCacheInvalidator.invalidateAfterCommit();
    return view;
  }

  @Transactional(readOnly = true)
  public SkuListResponse skus(Long productId) {
    requireProduct(productId);
    return new SkuListResponse(
        skuRepository.findAllByProductIdOrderByDisplayOrderAscIdAsc(productId).stream()
            .map(this::skuView)
            .toList());
  }

  @Transactional
  public SkuResponse createSku(Long productId, SkuCreateRequest request) {
    Product product = requireProduct(productId);
    if (request.compareAtPrice() != null
        && request.compareAtPrice().compareTo(request.price()) <= 0) {
      throw new AdminCatalogValidationException(List.of(error("compareAtPrice", "판매가보다 커야 합니다.")));
    }
    if (skuRepository.existsBySkuCode(request.skuCode())) {
      throw skuCodeConflict();
    }
    try {
      Sku sku =
          skuRepository.saveAndFlush(
              new Sku(
                  product,
                  request.skuCode(),
                  request.name(),
                  request.price(),
                  request.compareAtPrice(),
                  request.subscribable(),
                  request.displayOrder(),
                  request.status()));
      inventoryRepository.save(new InventoryEntity(sku.getId()));
      productListCacheInvalidator.invalidateAfterCommit();
      return skuView(sku);
    } catch (DataIntegrityViolationException exception) {
      throw skuCodeConflict();
    }
  }

  @Transactional
  public SkuResponse updateSku(Long productId, Long skuId, SkuPatchRequest request) {
    validate(request);
    requireProduct(productId);
    Sku sku =
        skuRepository
            .findByIdAndProductId(skuId, productId)
            .orElseThrow(
                () -> new AdminCatalogNotFoundException("SKU_NOT_FOUND", "SKU를 확인할 수 없습니다."));
    BigDecimal nextPrice = request.isPricePresent() ? request.getPrice() : sku.getPrice();
    BigDecimal nextCompareAtPrice =
        request.isCompareAtPricePresent() ? request.getCompareAtPrice() : sku.getCompareAtPrice();
    if (nextCompareAtPrice != null && nextCompareAtPrice.compareTo(nextPrice) <= 0) {
      throw new AdminCatalogValidationException(List.of(error("compareAtPrice", "판매가보다 커야 합니다.")));
    }
    sku.update(
        request.getName(),
        request.getPrice(),
        request.getCompareAtPrice(),
        request.isCompareAtPricePresent(),
        request.getSubscribable(),
        request.getDisplayOrder(),
        request.getStatus());
    SkuResponse view = skuView(skuRepository.saveAndFlush(sku));
    productListCacheInvalidator.invalidateAfterCommit();
    return view;
  }

  private Category requireCategory(Long categoryId) {
    return categoryRepository
        .findById(categoryId)
        .orElseThrow(
            () -> new AdminCatalogNotFoundException("CATEGORY_NOT_FOUND", "카테고리를 확인할 수 없습니다."));
  }

  private Category requireAssignableCategory(Long categoryId) {
    Category category = requireCategory(categoryId);
    if (!category.isActive() || SYSTEM_UNCATEGORIZED_SLUG.equals(category.getSlug())) {
      throw new AdminCatalogConflictException(
          "CATEGORY_NOT_ASSIGNABLE", "신규 상품에는 활성 실제 카테고리만 지정할 수 있습니다.");
    }
    return category;
  }

  private Category requireParentCategory(Long parentId, Long childId) {
    Category parent = requireCategory(parentId);
    if (childId != null && parent.getId().equals(childId)) {
      throw new AdminCatalogConflictException(
          "CATEGORY_PARENT_CONFLICT", "자기 자신을 상위 카테고리로 지정할 수 없습니다.");
    }
    for (Category ancestor = parent; ancestor != null; ancestor = ancestor.getParent()) {
      if (childId != null && ancestor.getId().equals(childId)) {
        throw new AdminCatalogConflictException(
            "CATEGORY_PARENT_CONFLICT", "하위 카테고리를 상위 카테고리로 지정할 수 없습니다.");
      }
    }
    if (parent.getParent() != null) {
      throw new AdminCatalogConflictException(
          "CATEGORY_DEPTH_EXCEEDED", "카테고리는 최대 2 depth까지만 지원합니다.");
    }
    return parent;
  }

  private Brand requireActiveBrand(Long brandId) {
    Brand brand =
        brandRepository
            .findById(brandId)
            .orElseThrow(
                () -> new AdminCatalogNotFoundException("BRAND_NOT_FOUND", "브랜드를 확인할 수 없습니다."));
    if (!brand.isActive()) {
      throw new AdminCatalogConflictException("BRAND_INACTIVE", "비활성 브랜드는 상품에 지정할 수 없습니다.");
    }
    return brand;
  }

  private Product requireProduct(Long productId) {
    return productRepository
        .findById(productId)
        .orElseThrow(
            () -> new AdminCatalogNotFoundException("PRODUCT_NOT_FOUND", "상품을 확인할 수 없습니다."));
  }

  private Product requireProductForUpdate(Long productId) {
    return productRepository
        .findByIdForUpdate(productId)
        .orElseThrow(
            () -> new AdminCatalogNotFoundException("PRODUCT_NOT_FOUND", "상품을 확인할 수 없습니다."));
  }

  private void requireFacetValuesCompatibleWithCategory(Long productId, Long categoryId) {
    if (catalogFacets.hasIncompatibleProductValues(productId, categoryId)) {
      throw new AdminCatalogConflictException(
          "PRODUCT_FACET_CATEGORY_CONFLICT", "현재 상품 facet 값이 새 카테고리에서 허용되지 않습니다.");
    }
  }

  private AdminCatalogConflictException slugConflict() {
    return new AdminCatalogConflictException("CATEGORY_SLUG_CONFLICT", "이미 사용 중인 카테고리 slug입니다.");
  }

  private AdminCatalogConflictException skuCodeConflict() {
    return new AdminCatalogConflictException("SKU_CODE_CONFLICT", "이미 사용 중인 SKU 코드입니다.");
  }

  private CategoryResponse categoryView(Category category) {
    return new CategoryResponse(
        category.getId(),
        category.getParent() == null ? null : category.getParent().getId(),
        category.getName(),
        category.getSlug(),
        category.getDisplayOrder(),
        category.isActive());
  }

  private BrandResponse brandView(Brand brand) {
    return new BrandResponse(
        brand.getId(),
        brand.getName(),
        brand.getSlug(),
        brand.getLogoUrl(),
        brand.isActive(),
        brand.getDisplayOrder());
  }

  private ProductResponse productView(Product product) {
    return new ProductResponse(
        product.getId(),
        product.getCategory() == null ? null : product.getCategory().getId(),
        product.getBrandId(),
        product.getName(),
        product.getShortDescription(),
        product.getDescription(),
        product.getPetType(),
        product.getThumbnailUrl(),
        product.getStatus());
  }

  private SkuResponse skuView(Sku sku) {
    return new SkuResponse(
        sku.getId(),
        sku.getProduct().getId(),
        sku.getSkuCode(),
        sku.getName(),
        sku.getPrice(),
        sku.getCompareAtPrice(),
        sku.isSubscribable(),
        sku.getDisplayOrder(),
        sku.getStatus());
  }

  private void validate(CategoryPatchRequest request) {
    List<FieldErrorResponse> errors = new ArrayList<>();
    if (!request.isNamePresent()
        && !request.isSlugPresent()
        && !request.isParentIdPresent()
        && !request.isDisplayOrderPresent()
        && !request.isActivePresent()) {
      errors.add(error("request", "수정할 필드를 하나 이상 입력해 주세요."));
    }
    validateRequiredText(errors, "name", request.isNamePresent(), request.getName(), 100);
    validateRequiredText(errors, "slug", request.isSlugPresent(), request.getSlug(), 100);
    if (request.isSlugPresent()
        && request.getSlug() != null
        && !SLUG_PATTERN.matcher(request.getSlug()).matches()) {
      errors.add(error("slug", "slug 형식이 올바르지 않습니다."));
    }
    validateNonnegative(
        errors, "displayOrder", request.isDisplayOrderPresent(), request.getDisplayOrder());
    validateRequired(errors, "active", request.isActivePresent(), request.getActive());
    throwIfInvalid(errors);
  }

  private void validate(ProductPatchRequest request) {
    List<FieldErrorResponse> errors = new ArrayList<>();
    if (!request.isCategoryIdPresent()
        && !request.isBrandIdPresent()
        && !request.isNamePresent()
        && !request.isShortDescriptionPresent()
        && !request.isDescriptionPresent()
        && !request.isPetTypePresent()
        && !request.isThumbnailUrlPresent()
        && !request.isStatusPresent()) {
      errors.add(error("request", "수정할 필드를 하나 이상 입력해 주세요."));
    }
    if (request.isCategoryIdPresent() && request.getCategoryId() == null) {
      errors.add(error("categoryId", "Category cannot be cleared."));
    }
    if (request.isCategoryIdPresent()
        && request.getCategoryId() != null
        && request.getCategoryId() <= 0) {
      errors.add(error("categoryId", "0보다 커야 합니다."));
    }
    validateRequiredText(errors, "name", request.isNamePresent(), request.getName(), 200);
    validateRequiredText(
        errors,
        "shortDescription",
        request.isShortDescriptionPresent(),
        request.getShortDescription(),
        500);
    validateNullableText(
        errors, "description", request.isDescriptionPresent(), request.getDescription(), 2000);
    validateRequiredText(errors, "petType", request.isPetTypePresent(), request.getPetType(), 20);
    validateNullableText(
        errors, "thumbnailUrl", request.isThumbnailUrlPresent(), request.getThumbnailUrl(), 2048);
    validateRequired(errors, "status", request.isStatusPresent(), request.getStatus());
    throwIfInvalid(errors);
  }

  private void validate(SkuPatchRequest request) {
    List<FieldErrorResponse> errors = new ArrayList<>();
    if (!request.isNamePresent()
        && !request.isPricePresent()
        && !request.isCompareAtPricePresent()
        && !request.isSubscribablePresent()
        && !request.isDisplayOrderPresent()
        && !request.isStatusPresent()) {
      errors.add(error("request", "수정할 필드를 하나 이상 입력해 주세요."));
    }
    validateRequiredText(errors, "name", request.isNamePresent(), request.getName(), 200);
    validatePrice(errors, "price", request.isPricePresent(), request.getPrice());
    if (request.isCompareAtPricePresent() && request.getCompareAtPrice() != null) {
      validatePrice(errors, "compareAtPrice", true, request.getCompareAtPrice());
    }
    validateRequired(
        errors, "subscribable", request.isSubscribablePresent(), request.getSubscribable());
    validateNonnegative(
        errors, "displayOrder", request.isDisplayOrderPresent(), request.getDisplayOrder());
    validateRequired(errors, "status", request.isStatusPresent(), request.getStatus());
    throwIfInvalid(errors);
  }

  private void validateRequiredText(
      List<FieldErrorResponse> errors, String field, boolean present, String value, int maxLength) {
    if (!present) return;
    if (value == null || value.isBlank()) {
      errors.add(error(field, "필수 입력입니다."));
    } else if (value.length() > maxLength) {
      errors.add(error(field, "길이가 허용 범위를 초과했습니다."));
    }
  }

  private void validateNullableText(
      List<FieldErrorResponse> errors, String field, boolean present, String value, int maxLength) {
    if (present && value != null && value.length() > maxLength) {
      errors.add(error(field, "길이가 허용 범위를 초과했습니다."));
    }
  }

  private void validateRequired(
      List<FieldErrorResponse> errors, String field, boolean present, Object value) {
    if (present && value == null) errors.add(error(field, "필수 입력입니다."));
  }

  private void validateNonnegative(
      List<FieldErrorResponse> errors, String field, boolean present, Integer value) {
    if (!present) return;
    if (value == null) errors.add(error(field, "필수 입력입니다."));
    else if (value < 0) errors.add(error(field, "0 이상이어야 합니다."));
  }

  private void validatePrice(
      List<FieldErrorResponse> errors, String field, boolean present, BigDecimal value) {
    if (!present) return;
    if (value == null) {
      errors.add(error(field, "필수 입력입니다."));
    } else if (value.signum() < 0 || value.scale() > 2 || value.precision() - value.scale() > 10) {
      errors.add(error(field, "0 이상이며 정수 10자리, 소수 2자리 이하여야 합니다."));
    }
  }

  private FieldErrorResponse error(String field, String message) {
    return new FieldErrorResponse(field, message);
  }

  private void throwIfInvalid(List<FieldErrorResponse> errors) {
    if (!errors.isEmpty()) throw new AdminCatalogValidationException(errors);
  }
}
