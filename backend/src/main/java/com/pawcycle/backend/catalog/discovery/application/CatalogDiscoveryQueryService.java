package com.pawcycle.backend.catalog.discovery.application;

import com.pawcycle.backend.catalog.discovery.persistence.CatalogDiscoveryQueryRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogDiscoveryQueryService {
  private final CatalogDiscoveryQueryRepository queries;

  public CatalogDiscoveryQueryService(CatalogDiscoveryQueryRepository queries) {
    this.queries = queries;
  }

  @Transactional(readOnly = true)
  public CatalogDiscoveryResponse findPublicDiscovery() {
    List<CatalogDiscoveryQueryRepository.CategoryRow> categories = queries.findCategories();
    return new CatalogDiscoveryResponse(
        toHierarchy(categories), queries.findBrands(), queries.findCategoryFacets());
  }

  private List<CatalogCategoryResponse> toHierarchy(
      List<CatalogDiscoveryQueryRepository.CategoryRow> rows) {
    Map<Long, List<CatalogDiscoveryQueryRepository.CategoryRow>> childrenByParent =
        new LinkedHashMap<>();
    for (CatalogDiscoveryQueryRepository.CategoryRow row : rows) {
      if (row.parentId() != null) {
        childrenByParent.computeIfAbsent(row.parentId(), ignored -> new ArrayList<>()).add(row);
      }
    }
    return rows.stream()
        .filter(row -> row.parentId() == null)
        .map(
            row ->
                new CatalogCategoryResponse(
                    row.categoryId(),
                    row.name(),
                    row.slug(),
                    row.displayOrder(),
                    childrenByParent.getOrDefault(row.categoryId(), List.of()).stream()
                        .map(
                            child ->
                                new CatalogChildCategoryResponse(
                                    child.categoryId(),
                                    child.name(),
                                    child.slug(),
                                    child.displayOrder()))
                        .toList()))
        .toList();
  }

}
