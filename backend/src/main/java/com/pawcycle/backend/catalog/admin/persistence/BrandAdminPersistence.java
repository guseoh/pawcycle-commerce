package com.pawcycle.backend.catalog.admin.persistence;

import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.BrandPatchCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.BrandView;
import com.pawcycle.backend.catalog.brand.domain.Brand;
import com.pawcycle.backend.catalog.brand.persistence.BrandRepository;
import com.pawcycle.backend.catalog.product.application.ProductListCacheInvalidator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class BrandAdminPersistence {
  private final BrandRepository brands;
  private final ProductListCacheInvalidator cacheInvalidator;

  public BrandAdminPersistence(BrandRepository brands, ProductListCacheInvalidator cacheInvalidator) {
    this.brands = brands;
    this.cacheInvalidator = cacheInvalidator;
  }

  @Transactional(readOnly = true)
  public BrandView brand(long brandId) {
    return toView(requireBrand(brandId));
  }

  @Transactional
  public BrandView updateBrand(long brandId, BrandPatchCommand request) {
    CatalogAdminValidation.requirePatch(
        request.namePresent()
            || request.slugPresent()
            || request.logoUrlPresent()
            || request.activePresent()
            || request.displayOrderPresent());
    Brand current = requireBrand(brandId);
    String name = request.namePresent() ? CatalogAdminValidation.requiredText(request.name(), "name", 150) : current.getName();
    String slug = request.slugPresent() ? CatalogAdminValidation.slug(request.slug(), "slug") : current.getSlug();
    String logoUrl = request.logoUrlPresent() ? CatalogAdminValidation.nullableText(request.logoUrl(), "logoUrl", 2048) : current.getLogoUrl();
    Boolean active = request.activePresent() ? CatalogAdminValidation.requiredBoolean(request.active(), "active") : current.isActive();
    Integer displayOrder = request.displayOrderPresent() ? CatalogAdminValidation.nonNegativeRequired(request.displayOrder(), "displayOrder") : current.getDisplayOrder();
    try {
      current.update(name, slug, logoUrl, true, active, displayOrder);
      brands.flush();
    } catch (DataIntegrityViolationException exception) {
      throw CatalogAdminValidation.conflict("BRAND_SLUG_CONFLICT", "이미 사용 중인 브랜드 slug입니다.");
    }
    cacheInvalidator.invalidateAfterCommit();
    return toView(current);
  }

  private Brand requireBrand(long brandId) {
    return brands
        .findById(brandId)
        .orElseThrow(() -> CatalogAdminValidation.missing("BRAND_NOT_FOUND", "브랜드를 확인할 수 없습니다."));
  }

  private BrandView toView(Brand brand) {
    return new BrandView(brand.getId(), brand.getName(), brand.getSlug(), brand.getLogoUrl(), brand.isActive(), brand.getDisplayOrder());
  }
}
