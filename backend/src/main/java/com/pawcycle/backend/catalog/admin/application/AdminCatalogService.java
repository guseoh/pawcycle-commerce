package com.pawcycle.backend.catalog.admin.application;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.BrandCreateCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.CategoryCreateCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.CategoryPatchCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.ProductCreateCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.ProductPatchCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.SkuCreateCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.SkuPatchCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.BrandView;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.CategoryView;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.ProductView;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.SkuView;

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
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminCatalogService {
  private static final String SYSTEM_UNCATEGORIZED_SLUG = "__pawcycle_uncategorized__";

  private final CategoryRepository categoryRepository;
  private final BrandRepository brandRepository;
  private final ProductRepository productRepository;
  private final SkuRepository skuRepository;
  private final InventoryRepository inventoryRepository;
  private final CatalogFacetPersistenceAdapter catalogFacets;
  private final ProductListCacheInvalidator productListCacheInvalidator;

  @Transactional(readOnly = true)
  public List<BrandView> brands() {
    return brandRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
            .map(this::brandView)
            .toList();
  }

  @Transactional
  public BrandView createBrand(
      BrandCreateCommand request) {
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
  public List<CategoryView> categories() {
    return categoryRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
            .map(this::categoryView)
            .toList();
  }

  @Transactional(readOnly = true)
  public CategoryView category(Long categoryId) {
    return categoryView(requireCategory(categoryId));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CategoryView createCategory(CategoryCreateCommand request) {
    if (categoryRepository.existsBySlug(request.slug())) {
      throw slugConflict();
    }
    // Preserve slug error precedence; acquire the parent lock before the child's FK insert.
    Category parent = request.parentId() == null ? null : requireCategoryForUpdate(request.parentId());
    if (parent != null) parent = requireParentCategory(request.parentId(), null);
    try {
      Category category = new Category(request.name(), request.slug(), request.displayOrder(), request.active());
      category.updateParent(parent);
      categoryRepository.saveAndFlush(category);
      productListCacheInvalidator.invalidateAfterCommit();
      return categoryView(category);
    } catch (DataIntegrityViolationException exception) {
      throw slugConflict();
    }
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CategoryView updateCategory(Long categoryId, CategoryPatchCommand request) {
    CatalogAdminPatchValidation.validate(request);
    Category category = lockCategoryAndParent(categoryId, request.parentIdPresent() ? request.parentId() : null);
    if (request.parentIdPresent()) {
      Category parent =
          request.parentId() == null
              ? null
              : requireParentCategory(request.parentId(), categoryId);
      // READ_COMMITTED observes children committed while the row locks were awaited.
      // Child creation/reparenting must lock this target as its new parent first.
      if (parent != null && categoryRepository.existsByParentId(categoryId)) {
        throw new AdminCatalogConflictException(
            "CATEGORY_DEPTH_EXCEEDED", "카테고리는 최대 2 depth까지만 지원합니다.");
      }
      category.updateParent(parent);
    }
    if (request.slugPresent()
        && categoryRepository.existsBySlugAndIdNot(request.slug(), categoryId)) {
      throw slugConflict();
    }
    category.update(
        request.name(), request.slug(), request.displayOrder(), request.active());
    try {
      CategoryView view = categoryView(categoryRepository.saveAndFlush(category));
      productListCacheInvalidator.invalidateAfterCommit();
      return view;
    } catch (DataIntegrityViolationException exception) {
      throw slugConflict();
    }
  }

  @Transactional(readOnly = true)
  public List<ProductView> products() {
    return productRepository.findAllWithCategoryOrderByIdAsc().stream()
            .map(this::productView)
            .toList();
  }

  @Transactional(readOnly = true)
  public ProductView product(Long productId) {
    return productView(requireProduct(productId));
  }

  @Transactional
  public ProductView createProduct(ProductCreateCommand request) {
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
  public ProductView updateProduct(Long productId, ProductPatchCommand request) {
    CatalogAdminPatchValidation.validate(request);
    Product product = requireProductForUpdate(productId);
    Category category =
        request.categoryIdPresent() && request.categoryId() != null
            ? requireAssignableCategory(request.categoryId())
            : null;
    if (request.categoryIdPresent()
        && category != null
        && (product.getCategory() == null
            || !category.getId().equals(product.getCategory().getId()))) {
      requireFacetValuesCompatibleWithCategory(productId, category.getId());
    }
    if (request.brandIdPresent()) {
      if (request.brandId() == null) {
        throw new AdminCatalogValidationException(
            List.of(error("brandId", "Brand cannot be cleared.")));
      }
      requireActiveBrand(request.brandId());
      product.updateBrandId(request.brandId());
    }
    product.update(
        category,
        request.categoryIdPresent(),
        request.name(),
        request.shortDescription(),
        request.description(),
        request.descriptionPresent(),
        request.petType(),
        request.thumbnailUrl(),
        request.thumbnailUrlPresent());
    if (request.statusPresent()) {
      if (!product.canTransitionTo(request.status())) {
        throw new AdminCatalogConflictException(
            "PRODUCT_STATUS_TRANSITION_CONFLICT", "허용되지 않은 상품 상태 전이입니다.");
      }
      product.transitionTo(request.status());
    }
    ProductView view = productView(productRepository.saveAndFlush(product));
    productListCacheInvalidator.invalidateAfterCommit();
    return view;
  }

  @Transactional(readOnly = true)
  public List<SkuView> skus(Long productId) {
    requireProduct(productId);
    return skuRepository.findAllByProductIdOrderByDisplayOrderAscIdAsc(productId).stream()
            .map(this::skuView)
            .toList();
  }

  @Transactional
  public SkuView createSku(Long productId, SkuCreateCommand request) {
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
  public SkuView updateSku(Long productId, Long skuId, SkuPatchCommand request) {
    CatalogAdminPatchValidation.validate(request);
    requireProduct(productId);
    Sku sku =
        skuRepository
            .findByIdAndProductId(skuId, productId)
            .orElseThrow(
                () -> new AdminCatalogNotFoundException("SKU_NOT_FOUND", "SKU를 확인할 수 없습니다."));
    BigDecimal nextPrice = request.pricePresent() ? request.price() : sku.getPrice();
    BigDecimal nextCompareAtPrice =
        request.compareAtPricePresent() ? request.compareAtPrice() : sku.getCompareAtPrice();
    if (nextCompareAtPrice != null && nextCompareAtPrice.compareTo(nextPrice) <= 0) {
      throw new AdminCatalogValidationException(List.of(error("compareAtPrice", "판매가보다 커야 합니다.")));
    }
    sku.update(
        request.name(),
        request.price(),
        request.compareAtPrice(),
        request.compareAtPricePresent(),
        request.subscribable(),
        request.displayOrder(),
        request.status());
    SkuView view = skuView(skuRepository.saveAndFlush(sku));
    productListCacheInvalidator.invalidateAfterCommit();
    return view;
  }

  private Category requireCategory(Long categoryId) {
    return categoryRepository
        .findById(categoryId)
        .orElseThrow(
            () -> new AdminCatalogNotFoundException("CATEGORY_NOT_FOUND", "카테고리를 확인할 수 없습니다."));
  }

  private Category requireCategoryForUpdate(Long categoryId) {
    return categoryRepository.findByIdForUpdate(categoryId)
        .orElseThrow(() -> new AdminCatalogNotFoundException("CATEGORY_NOT_FOUND", "카테고리를 확인할 수 없습니다."));
  }

  private Category lockCategoryAndParent(Long categoryId, Long parentId) {
    // Separate PK lookups make acquisition order explicit, independent of query plans.
    if (parentId != null && parentId < categoryId) requireCategoryForUpdate(parentId);
    Category category = requireCategoryForUpdate(categoryId);
    if (parentId != null && parentId > categoryId) requireCategoryForUpdate(parentId);
    return category;
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

  private CategoryView categoryView(Category category) {
    return new CategoryView(
        category.getId(),
        category.getParent() == null ? null : category.getParent().getId(),
        category.getName(),
        category.getSlug(),
        category.getDisplayOrder(),
        category.isActive());
  }

  private BrandView brandView(Brand brand) {
    return new BrandView(
        brand.getId(),
        brand.getName(),
        brand.getSlug(),
        brand.getLogoUrl(),
        brand.isActive(),
        brand.getDisplayOrder());
  }

  private ProductView productView(Product product) {
    return new ProductView(
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

  private SkuView skuView(Sku sku) {
    return new SkuView(
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

  private FieldErrorResponse error(String field, String message) {
    return new FieldErrorResponse(field, message);
  }
}
