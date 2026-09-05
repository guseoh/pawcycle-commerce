package com.pawcycle.backend.catalog.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "facet_options")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FacetOptionEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "facet_definition_id", nullable = false)
  private FacetDefinitionEntity facetDefinition;

  @Column(nullable = false, length = 100)
  private String value;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  public FacetOptionEntity(FacetDefinitionEntity facetDefinition, String value, int displayOrder) {
    this.facetDefinition = facetDefinition;
    this.value = value;
    this.displayOrder = displayOrder;
  }

  public void update(String value, int displayOrder) {
    this.value = value;
    this.displayOrder = displayOrder;
  }
}
