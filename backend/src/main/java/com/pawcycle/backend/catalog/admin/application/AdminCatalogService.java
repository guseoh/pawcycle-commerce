package com.pawcycle.backend.catalog.admin.application;

import com.pawcycle.backend.catalog.admin.api.AdminCatalogRequests.CategoryCreate;
import com.pawcycle.backend.catalog.admin.api.AdminCatalogRequests.CategoryPatch;
import com.pawcycle.backend.catalog.admin.api.AdminCatalogRequests.ProductCreate;
import com.pawcycle.backend.catalog.admin.api.AdminCatalogRequests.ProductPatch;
import com.pawcycle.backend.catalog.admin.api.AdminCatalogRequests.SkuCreate;
import com.pawcycle.backend.catalog.admin.api.AdminCatalogRequests.SkuPatch;
import com.pawcycle.backend.catalog.admin.api.AdminCatalogViews;
import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.infra.CategoryRepository;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.infra.SkuRepository;
import com.pawcycle.backend.common.error.FieldErrorResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminCatalogService {
	private static final Pattern SLUG_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

	private final CategoryRepository categoryRepository;
	private final ProductRepository productRepository;
	private final SkuRepository skuRepository;
	private final JdbcTemplate jdbcTemplate;

	@Transactional(readOnly = true)
	public AdminCatalogViews.CategoryList categories() {
		return new AdminCatalogViews.CategoryList(categoryRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
				.map(this::categoryView)
				.toList());
	}

	@Transactional(readOnly = true)
	public AdminCatalogViews.Category category(Long categoryId) {
		return categoryView(requireCategory(categoryId));
	}

	@Transactional
	public AdminCatalogViews.Category createCategory(CategoryCreate request) {
		if (categoryRepository.existsBySlug(request.slug())) {
			throw slugConflict();
		}
		try {
			Category category = categoryRepository.saveAndFlush(new Category(
					request.name(), request.slug(), request.displayOrder(), request.active()));
			return categoryView(category);
		} catch (DataIntegrityViolationException exception) {
			throw slugConflict();
		}
	}

	@Transactional
	public AdminCatalogViews.Category updateCategory(Long categoryId, CategoryPatch request) {
		validate(request);
		Category category = requireCategory(categoryId);
		if (request.isSlugPresent()
				&& categoryRepository.existsBySlugAndIdNot(request.getSlug(), categoryId)) {
			throw slugConflict();
		}
		category.update(request.getName(), request.getSlug(), request.getDisplayOrder(), request.getActive());
		try {
			return categoryView(categoryRepository.saveAndFlush(category));
		} catch (DataIntegrityViolationException exception) {
			throw slugConflict();
		}
	}

	@Transactional(readOnly = true)
	public AdminCatalogViews.ProductList products() {
		return new AdminCatalogViews.ProductList(productRepository.findAllWithCategoryOrderByIdAsc().stream()
				.map(this::productView)
				.toList());
	}

	@Transactional(readOnly = true)
	public AdminCatalogViews.Product product(Long productId) {
		return productView(requireProduct(productId));
	}

	@Transactional
	public AdminCatalogViews.Product createProduct(ProductCreate request) {
		Category category = requireAssignableCategory(request.categoryId());
		Product product = productRepository.saveAndFlush(new Product(
				category,
				request.name(),
				request.shortDescription(),
				request.description(),
				request.petType(),
				request.thumbnailUrl()));
		return productView(product);
	}

	@Transactional
	public AdminCatalogViews.Product updateProduct(Long productId, ProductPatch request) {
		validate(request);
		Product product = requireProductForUpdate(productId);
		Category category = request.isCategoryIdPresent() && request.getCategoryId() != null
				? requireAssignableCategory(request.getCategoryId())
				: null;
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
						"PRODUCT_STATUS_TRANSITION_CONFLICT",
						"허용되지 않은 상품 상태 전이입니다.");
			}
			product.transitionTo(request.getStatus());
		}
		return productView(productRepository.saveAndFlush(product));
	}

	@Transactional(readOnly = true)
	public AdminCatalogViews.SkuList skus(Long productId) {
		requireProduct(productId);
		return new AdminCatalogViews.SkuList(
				skuRepository.findAllByProductIdOrderByDisplayOrderAscIdAsc(productId).stream()
						.map(this::skuView)
						.toList());
	}

	@Transactional
	public AdminCatalogViews.Sku createSku(Long productId, SkuCreate request) {
		Product product = requireProduct(productId);
		if (skuRepository.existsBySkuCode(request.skuCode())) {
			throw skuCodeConflict();
		}
		try {
			Sku sku = skuRepository.saveAndFlush(new Sku(
					product,
					request.skuCode(),
					request.name(),
					request.price(),
					request.subscribable(),
					request.displayOrder(),
					request.status()));
			jdbcTemplate.update("INSERT INTO inventories(sku_id,available_quantity,reserved_quantity,version) VALUES (?,0,0,0)", sku.getId());
			return skuView(sku);
		} catch (DataIntegrityViolationException exception) {
			throw skuCodeConflict();
		}
	}

	@Transactional
	public AdminCatalogViews.Sku updateSku(Long productId, Long skuId, SkuPatch request) {
		validate(request);
		requireProduct(productId);
		Sku sku = skuRepository.findByIdAndProductId(skuId, productId)
				.orElseThrow(() -> new AdminCatalogNotFoundException("SKU_NOT_FOUND", "SKU를 확인할 수 없습니다."));
		sku.update(
				request.getName(),
				request.getPrice(),
				request.getSubscribable(),
				request.getDisplayOrder(),
				request.getStatus());
		return skuView(skuRepository.saveAndFlush(sku));
	}

	private Category requireCategory(Long categoryId) {
		return categoryRepository.findById(categoryId)
				.orElseThrow(() -> new AdminCatalogNotFoundException(
						"CATEGORY_NOT_FOUND", "카테고리를 확인할 수 없습니다."));
	}

	private Category requireAssignableCategory(Long categoryId) {
		Category category = requireCategory(categoryId);
		if (!category.isActive() || "uncategorized".equals(category.getSlug())) {
			throw new AdminCatalogConflictException(
					"CATEGORY_NOT_ASSIGNABLE", "신규 상품에는 활성 실제 카테고리만 지정할 수 있습니다.");
		}
		return category;
	}

	private Product requireProduct(Long productId) {
		return productRepository.findById(productId)
				.orElseThrow(() -> new AdminCatalogNotFoundException(
						"PRODUCT_NOT_FOUND", "상품을 확인할 수 없습니다."));
	}

	private Product requireProductForUpdate(Long productId) {
		return productRepository.findByIdForUpdate(productId)
				.orElseThrow(() -> new AdminCatalogNotFoundException(
						"PRODUCT_NOT_FOUND", "상품을 확인할 수 없습니다."));
	}

	private AdminCatalogConflictException slugConflict() {
		return new AdminCatalogConflictException("CATEGORY_SLUG_CONFLICT", "이미 사용 중인 카테고리 slug입니다.");
	}

	private AdminCatalogConflictException skuCodeConflict() {
		return new AdminCatalogConflictException("SKU_CODE_CONFLICT", "이미 사용 중인 SKU 코드입니다.");
	}

	private AdminCatalogViews.Category categoryView(Category category) {
		return new AdminCatalogViews.Category(
				category.getId(), category.getName(), category.getSlug(), category.getDisplayOrder(), category.isActive());
	}

	private AdminCatalogViews.Product productView(Product product) {
		return new AdminCatalogViews.Product(
				product.getId(),
				product.getCategory() == null ? null : product.getCategory().getId(),
				product.getName(),
				product.getShortDescription(),
				product.getDescription(),
				product.getPetType(),
				product.getThumbnailUrl(),
				product.getStatus());
	}

	private AdminCatalogViews.Sku skuView(Sku sku) {
		return new AdminCatalogViews.Sku(
				sku.getId(),
				sku.getProduct().getId(),
				sku.getSkuCode(),
				sku.getName(),
				sku.getPrice(),
				sku.isSubscribable(),
				sku.getDisplayOrder(),
				sku.getStatus());
	}

	private void validate(CategoryPatch request) {
		List<FieldErrorResponse> errors = new ArrayList<>();
		if (!request.isNamePresent() && !request.isSlugPresent()
				&& !request.isDisplayOrderPresent() && !request.isActivePresent()) {
			errors.add(error("request", "수정할 필드를 하나 이상 입력해 주세요."));
		}
		validateRequiredText(errors, "name", request.isNamePresent(), request.getName(), 100);
		validateRequiredText(errors, "slug", request.isSlugPresent(), request.getSlug(), 100);
		if (request.isSlugPresent() && request.getSlug() != null && !SLUG_PATTERN.matcher(request.getSlug()).matches()) {
			errors.add(error("slug", "slug 형식이 올바르지 않습니다."));
		}
		validateNonnegative(errors, "displayOrder", request.isDisplayOrderPresent(), request.getDisplayOrder());
		validateRequired(errors, "active", request.isActivePresent(), request.getActive());
		throwIfInvalid(errors);
	}

	private void validate(ProductPatch request) {
		List<FieldErrorResponse> errors = new ArrayList<>();
		if (!request.isCategoryIdPresent() && !request.isNamePresent() && !request.isShortDescriptionPresent()
				&& !request.isDescriptionPresent() && !request.isPetTypePresent()
				&& !request.isThumbnailUrlPresent() && !request.isStatusPresent()) {
			errors.add(error("request", "수정할 필드를 하나 이상 입력해 주세요."));
		}
		if (request.isCategoryIdPresent() && request.getCategoryId() == null) {
			errors.add(error("categoryId", "Category cannot be cleared."));
		}
		if (request.isCategoryIdPresent() && request.getCategoryId() != null && request.getCategoryId() <= 0) {
			errors.add(error("categoryId", "0보다 커야 합니다."));
		}
		validateRequiredText(errors, "name", request.isNamePresent(), request.getName(), 200);
		validateRequiredText(errors, "shortDescription", request.isShortDescriptionPresent(), request.getShortDescription(), 500);
		validateNullableText(errors, "description", request.isDescriptionPresent(), request.getDescription(), 2000);
		validateRequiredText(errors, "petType", request.isPetTypePresent(), request.getPetType(), 20);
		validateNullableText(errors, "thumbnailUrl", request.isThumbnailUrlPresent(), request.getThumbnailUrl(), 2048);
		validateRequired(errors, "status", request.isStatusPresent(), request.getStatus());
		throwIfInvalid(errors);
	}

	private void validate(SkuPatch request) {
		List<FieldErrorResponse> errors = new ArrayList<>();
		if (!request.isNamePresent() && !request.isPricePresent() && !request.isSubscribablePresent()
				&& !request.isDisplayOrderPresent() && !request.isStatusPresent()) {
			errors.add(error("request", "수정할 필드를 하나 이상 입력해 주세요."));
		}
		validateRequiredText(errors, "name", request.isNamePresent(), request.getName(), 200);
		validatePrice(errors, request.isPricePresent(), request.getPrice());
		validateRequired(errors, "subscribable", request.isSubscribablePresent(), request.getSubscribable());
		validateNonnegative(errors, "displayOrder", request.isDisplayOrderPresent(), request.getDisplayOrder());
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

	private void validateRequired(List<FieldErrorResponse> errors, String field, boolean present, Object value) {
		if (present && value == null) errors.add(error(field, "필수 입력입니다."));
	}

	private void validateNonnegative(
			List<FieldErrorResponse> errors, String field, boolean present, Integer value) {
		if (!present) return;
		if (value == null) errors.add(error(field, "필수 입력입니다."));
		else if (value < 0) errors.add(error(field, "0 이상이어야 합니다."));
	}

	private void validatePrice(List<FieldErrorResponse> errors, boolean present, BigDecimal value) {
		if (!present) return;
		if (value == null) {
			errors.add(error("price", "필수 입력입니다."));
		} else if (value.signum() < 0 || value.scale() > 2 || value.precision() - value.scale() > 10) {
			errors.add(error("price", "0 이상이며 정수 10자리, 소수 2자리 이하여야 합니다."));
		}
	}

	private FieldErrorResponse error(String field, String message) {
		return new FieldErrorResponse(field, message);
	}

	private void throwIfInvalid(List<FieldErrorResponse> errors) {
		if (!errors.isEmpty()) throw new AdminCatalogValidationException(errors);
	}
}
