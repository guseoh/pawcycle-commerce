package com.pawcycle.backend.subscription.api;

import java.util.List;

public record PlanVersionResponse(
    long planId,
    String planName,
    String targetPetType,
    long planVersionId,
    long packagePriceKrw,
    List<PlanItemResponse> items,
    List<Integer> allowedDeliveryCycleWeeks,
    PlanSaleResponse sale) {}
