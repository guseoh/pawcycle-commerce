package com.pawcycle.backend.catalog.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public record CategoryFacetId(
    @Column(name = "category_id") Long categoryId,
    @Column(name = "facet_definition_id") Long facetDefinitionId)
    implements Serializable {}
