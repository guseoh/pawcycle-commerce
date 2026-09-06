package com.pawcycle.backend.catalog.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public record ProductFacetValueId(
    @Column(name = "product_id") Long productId,
    @Column(name = "facet_option_id") Long facetOptionId)
    implements Serializable {}
