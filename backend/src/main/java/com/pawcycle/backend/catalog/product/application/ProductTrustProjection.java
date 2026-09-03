package com.pawcycle.backend.catalog.product.application;

import java.math.BigDecimal;

public record ProductTrustProjection(BigDecimal averageRating, long reviewCount, long questionCount) {}
