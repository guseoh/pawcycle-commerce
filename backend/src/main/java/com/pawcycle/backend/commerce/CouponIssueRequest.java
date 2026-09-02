package com.pawcycle.backend.commerce;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CouponIssueRequest(@NotNull @Positive Long memberId) {}
