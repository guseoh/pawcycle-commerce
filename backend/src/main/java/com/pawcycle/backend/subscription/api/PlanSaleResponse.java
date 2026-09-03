package com.pawcycle.backend.subscription.api;

import java.time.LocalDate;

public record PlanSaleResponse(boolean onSale, LocalDate startsOn, LocalDate endsOn) {}
