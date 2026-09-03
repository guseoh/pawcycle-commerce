package com.pawcycle.backend.subscription;

import java.time.LocalDate;

public record PlanVersionProjection(
    long planId,
    String planName,
    Long currentPlanVersionId,
    String targetPetType,
    boolean onSale,
    LocalDate saleStartsOn,
    LocalDate saleEndsOn,
    long id,
    long packagePriceKrw,
    boolean migrationOnly) {}
