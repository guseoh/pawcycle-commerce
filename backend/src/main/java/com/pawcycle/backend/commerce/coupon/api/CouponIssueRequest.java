package com.pawcycle.backend.commerce.coupon.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CouponIssueRequest(@NotNull @Positive Long memberId) {}
