package com.pawcycle.backend.catalog.admin.persistence;

import com.pawcycle.backend.catalog.admin.application.AdminCatalogConflictException;
import com.pawcycle.backend.catalog.admin.application.AdminCatalogNotFoundException;
import com.pawcycle.backend.catalog.admin.application.AdminCatalogValidationException;
import com.pawcycle.backend.common.error.FieldErrorResponse;
import java.util.LinkedHashSet;
import java.util.List;

final class CatalogAdminValidation {
  private CatalogAdminValidation() {}

  static AdminCatalogNotFoundException missing(String code, String message) {
    return new AdminCatalogNotFoundException(code, message);
  }

  static AdminCatalogConflictException conflict(String code, String message) {
    return new AdminCatalogConflictException(code, message);
  }

  static AdminCatalogValidationException validation(String field, String message) {
    return new AdminCatalogValidationException(List.of(new FieldErrorResponse(field, message)));
  }

  static void requirePatch(boolean hasField) {
    if (!hasField) throw validation("request", "수정할 필드를 하나 이상 입력해 주세요.");
  }

  static String requiredText(String value, String field, int maxLength) {
    if (value == null || value.isBlank()) throw validation(field, "필수 입력입니다.");
    if (value.length() > maxLength) throw validation(field, "길이가 허용 범위를 초과했습니다.");
    return value;
  }

  static String nullableText(String value, String field, int maxLength) {
    if (value != null && value.length() > maxLength) throw validation(field, "길이가 허용 범위를 초과했습니다.");
    return value;
  }

  static boolean requiredBoolean(Boolean value, String field) {
    if (value == null) throw validation(field, "필수 입력입니다.");
    return value;
  }

  static String slug(String value, String field) {
    requiredText(value, field, 100);
    if (!value.matches("[a-z0-9]+(?:-[a-z0-9]+)*"))
      throw validation(field, "slug 형식이 올바르지 않습니다.");
    return value;
  }

  static String imageType(String value) {
    if (value == null) throw validation("imageType", "필수 입력입니다.");
    if (!"MAIN".equals(value) && !"DETAIL".equals(value))
      throw validation("imageType", "MAIN 또는 DETAIL이어야 합니다.");
    return value;
  }

  static int nonNegativeRequired(Integer value, String field) {
    if (value == null) throw validation(field, "필수 입력입니다.");
    if (value < 0) throw validation(field, "0 이상이어야 합니다.");
    return value;
  }

  static List<Long> distinct(List<Long> values, String field) {
    if (values == null) throw validation(field, "필수 입력입니다.");
    if (new LinkedHashSet<>(values).size() != values.size())
      throw validation(field, "중복 값은 허용되지 않습니다.");
    return List.copyOf(values);
  }
}
