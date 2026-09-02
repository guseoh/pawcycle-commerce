package com.pawcycle.backend.commerce;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record MembershipGradeRequest(
    @NotBlank @Size(max = 30) String code,
    @NotBlank @Size(max = 100) String name,
    @NotNull @DecimalMin("0.00") BigDecimal minimumPurchaseAmount,
    @NotNull Integer displayOrder,
    @NotNull Boolean active,
    Long benefitCouponId) {}
