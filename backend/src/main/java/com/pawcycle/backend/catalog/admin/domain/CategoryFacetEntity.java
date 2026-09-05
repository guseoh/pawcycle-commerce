package com.pawcycle.backend.catalog.admin.domain;

import com.pawcycle.backend.catalog.category.domain.Category;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "category_facets")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class CategoryFacetEntity {
  @EmbeddedId private CategoryFacetId id;

  @MapsId("categoryId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "category_id", nullable = false)
  private Category category;

  @MapsId("facetDefinitionId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "facet_definition_id", nullable = false)
  private FacetDefinitionEntity facetDefinition;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  public CategoryFacetEntity(Category category, FacetDefinitionEntity facetDefinition, int displayOrder) {
    this.category = category;
    this.facetDefinition = facetDefinition;
    this.id = new CategoryFacetId(category.getId(), facetDefinition.getId());
    this.displayOrder = displayOrder;
  }

  public void updateDisplayOrder(int displayOrder) {
    this.displayOrder = displayOrder;
  }
}
