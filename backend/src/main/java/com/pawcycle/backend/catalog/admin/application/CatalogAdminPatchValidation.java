package com.pawcycle.backend.catalog.admin.application;

import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.CategoryPatchCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.ProductPatchCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.SkuPatchCommand;
import com.pawcycle.backend.common.error.FieldErrorResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Pure PATCH presence and scalar validation; mutation rules remain with their existing owners. */
final class CatalogAdminPatchValidation {
  private static final Pattern SLUG_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

  private CatalogAdminPatchValidation() {}

  static void validate(CategoryPatchCommand request) {
    List<FieldErrorResponse> errors = new ArrayList<>();
    if (!request.namePresent()
        && !request.slugPresent()
        && !request.parentIdPresent()
        && !request.displayOrderPresent()
        && !request.activePresent()) {
      errors.add(error("request", "수정할 필드를 하나 이상 입력해 주세요."));
    }
    validateRequiredText(errors, "name", request.namePresent(), request.name(), 100);
    validateRequiredText(errors, "slug", request.slugPresent(), request.slug(), 100);
    if (request.slugPresent()
        && request.slug() != null
        && !SLUG_PATTERN.matcher(request.slug()).matches()) {
      errors.add(error("slug", "slug 형식이 올바르지 않습니다."));
    }
    validateNonnegative(
        errors, "displayOrder", request.displayOrderPresent(), request.displayOrder());
    validateRequired(errors, "active", request.activePresent(), request.active());
    throwIfInvalid(errors);
  }

  static void validate(ProductPatchCommand request) {
    List<FieldErrorResponse> errors = new ArrayList<>();
    if (!request.categoryIdPresent()
        && !request.brandIdPresent()
        && !request.namePresent()
        && !request.shortDescriptionPresent()
        && !request.descriptionPresent()
        && !request.petTypePresent()
        && !request.thumbnailUrlPresent()
        && !request.statusPresent()) {
      errors.add(error("request", "수정할 필드를 하나 이상 입력해 주세요."));
    }
    if (request.categoryIdPresent() && request.categoryId() == null) {
      errors.add(error("categoryId", "Category cannot be cleared."));
    }
    if (request.categoryIdPresent()
        && request.categoryId() != null
        && request.categoryId() <= 0) {
      errors.add(error("categoryId", "0보다 커야 합니다."));
    }
    validateRequiredText(errors, "name", request.namePresent(), request.name(), 200);
    validateRequiredText(
        errors,
        "shortDescription",
        request.shortDescriptionPresent(),
        request.shortDescription(),
        500);
    validateNullableText(
        errors, "description", request.descriptionPresent(), request.description(), 2000);
    validateRequiredText(errors, "petType", request.petTypePresent(), request.petType(), 20);
    validateNullableText(
        errors, "thumbnailUrl", request.thumbnailUrlPresent(), request.thumbnailUrl(), 2048);
    validateRequired(errors, "status", request.statusPresent(), request.status());
    throwIfInvalid(errors);
  }

  static void validate(SkuPatchCommand request) {
    List<FieldErrorResponse> errors = new ArrayList<>();
    if (!request.namePresent()
        && !request.pricePresent()
        && !request.compareAtPricePresent()
        && !request.subscribablePresent()
        && !request.displayOrderPresent()
        && !request.statusPresent()) {
      errors.add(error("request", "수정할 필드를 하나 이상 입력해 주세요."));
    }
    validateRequiredText(errors, "name", request.namePresent(), request.name(), 200);
    validatePrice(errors, "price", request.pricePresent(), request.price());
    if (request.compareAtPricePresent() && request.compareAtPrice() != null) {
      validatePrice(errors, "compareAtPrice", true, request.compareAtPrice());
    }
    validateRequired(
        errors, "subscribable", request.subscribablePresent(), request.subscribable());
    validateNonnegative(
        errors, "displayOrder", request.displayOrderPresent(), request.displayOrder());
    validateRequired(errors, "status", request.statusPresent(), request.status());
    throwIfInvalid(errors);
  }

  private static void validateRequiredText(
      List<FieldErrorResponse> errors, String field, boolean present, String value, int maxLength) {
    if (!present) return;
    if (value == null || value.isBlank()) {
      errors.add(error(field, "필수 입력입니다."));
    } else if (value.length() > maxLength) {
      errors.add(error(field, "길이가 허용 범위를 초과했습니다."));
    }
  }

  private static void validateNullableText(
      List<FieldErrorResponse> errors, String field, boolean present, String value, int maxLength) {
    if (present && value != null && value.length() > maxLength) {
      errors.add(error(field, "길이가 허용 범위를 초과했습니다."));
    }
  }

  private static void validateRequired(
      List<FieldErrorResponse> errors, String field, boolean present, Object value) {
    if (present && value == null) errors.add(error(field, "필수 입력입니다."));
  }

  private static void validateNonnegative(
      List<FieldErrorResponse> errors, String field, boolean present, Integer value) {
    if (!present) return;
    if (value == null) errors.add(error(field, "필수 입력입니다."));
    else if (value < 0) errors.add(error(field, "0 이상이어야 합니다."));
  }

  private static void validatePrice(
      List<FieldErrorResponse> errors, String field, boolean present, BigDecimal value) {
    if (!present) return;
    if (value == null) {
      errors.add(error(field, "필수 입력입니다."));
    } else if (value.signum() < 0 || value.scale() > 2 || value.precision() - value.scale() > 10) {
      errors.add(error(field, "0 이상이며 정수 10자리, 소수 2자리 이하여야 합니다."));
    }
  }

  private static FieldErrorResponse error(String field, String message) {
    return new FieldErrorResponse(field, message);
  }

  private static void throwIfInvalid(List<FieldErrorResponse> errors) {
    if (!errors.isEmpty()) throw new AdminCatalogValidationException(errors);
  }
}
