package com.pawcycle.backend.catalog.discovery.persistence;

import com.pawcycle.backend.catalog.admin.domain.CategoryFacetEntity;
import com.pawcycle.backend.catalog.admin.persistence.CategoryFacetRepository;
import com.pawcycle.backend.catalog.admin.persistence.FacetOptionRepository;
import com.pawcycle.backend.catalog.brand.persistence.BrandRepository;
import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.persistence.CategoryRepository;
import com.pawcycle.backend.catalog.discovery.application.CatalogBrandResponse;
import com.pawcycle.backend.catalog.discovery.application.CatalogCategoryFacetsResponse;
import com.pawcycle.backend.catalog.discovery.application.CatalogFacetOptionResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CatalogDiscoveryQueryRepository {
  private static final String SYSTEM_UNCATEGORIZED_SLUG = "__pawcycle_uncategorized__";
  private final CategoryRepository categories;
  private final BrandRepository brands;
  private final CategoryFacetRepository categoryFacets;
  private final FacetOptionRepository facetOptions;

  public CatalogDiscoveryQueryRepository(
      CategoryRepository categories,
      BrandRepository brands,
      CategoryFacetRepository categoryFacets,
      FacetOptionRepository facetOptions) {
    this.categories = categories;
    this.brands = brands;
    this.categoryFacets = categoryFacets;
    this.facetOptions = facetOptions;
  }

  @Transactional(readOnly = true)
  public List<CategoryRow> findCategories() {
    return visibleCategories().stream()
        .map(
            category ->
                new CategoryRow(
                    category.getId(),
                    category.getParent() == null ? null : category.getParent().getId(),
                    category.getName(),
                    category.getSlug(),
                    category.getDisplayOrder()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<CatalogBrandResponse> findBrands() {
    return brands.findAllByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
        .map(
            brand ->
                new CatalogBrandResponse(
                    brand.getId(),
                    brand.getName(),
                    brand.getSlug(),
                    brand.getLogoUrl(),
                    brand.getDisplayOrder()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<CatalogCategoryFacetsResponse> findCategoryFacets() {
    List<CatalogCategoryFacetsResponse> result = new ArrayList<>();
    for (Category category : visibleCategories()) {
      List<CatalogFacetResponseBuilder> facets = new ArrayList<>();
      for (CategoryFacetEntity categoryFacet : categoryFacets.findAllOrdered(category.getId())) {
        var definition = categoryFacet.getFacetDefinition();
        facets.add(
            new CatalogFacetResponseBuilder(
                definition.getKey(),
                definition.getName(),
                categoryFacet.getDisplayOrder(),
                facetOptions.findByFacetDefinition_IdOrderByDisplayOrderAscIdAsc(definition.getId()).stream()
                    .map(option -> new CatalogFacetOptionResponse(option.getId(), option.getValue(), option.getDisplayOrder()))
                    .toList()));
      }
      result.add(
          new CatalogCategoryFacetsResponse(
              category.getSlug(),
              facets.stream()
                  .map(facet -> new com.pawcycle.backend.catalog.discovery.application.CatalogFacetResponse(facet.key, facet.name, facet.displayOrder, facet.options))
                  .toList()));
    }
    return result;
  }

  private List<Category> visibleCategories() {
    return categories.findAllByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
        .filter(this::isVisibleCategory)
        .toList();
  }

  private boolean isVisibleCategory(Category category) {
    if (SYSTEM_UNCATEGORIZED_SLUG.equals(category.getSlug())) return false;
    Category parent = category.getParent();
    return parent == null
        || (parent.isActive()
            && !SYSTEM_UNCATEGORIZED_SLUG.equals(parent.getSlug())
            && parent.getParent() == null);
  }

  public record CategoryRow(Long categoryId, Long parentId, String name, String slug, int displayOrder) {}

  private record CatalogFacetResponseBuilder(
      String key, String name, int displayOrder, List<CatalogFacetOptionResponse> options) {}
}
