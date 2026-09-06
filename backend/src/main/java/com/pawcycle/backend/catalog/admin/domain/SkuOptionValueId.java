package com.pawcycle.backend.catalog.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public record SkuOptionValueId(
    @Column(name = "sku_id") Long skuId, @Column(name = "option_value_id") Long optionValueId)
    implements Serializable {}
