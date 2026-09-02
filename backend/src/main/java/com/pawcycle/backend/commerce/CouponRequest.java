package com.pawcycle.backend.commerce;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CouponRequest(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Pattern(regexp = "FIXED_AMOUNT|PERCENTAGE") String discountType,
    @NotNull @DecimalMin("0.00") BigDecimal discountValue,
    @NotNull @DecimalMin("0.00") BigDecimal minimumOrderAmount,
    @DecimalMin("0.00") BigDecimal maximumDiscountAmount,
    @NotNull LocalDateTime validFrom,
    @NotNull LocalDateTime validUntil,
    @NotNull Boolean active) {
  @AssertTrue
  public boolean isValidRange() {
    return validFrom == null || validUntil == null || validFrom.isBefore(validUntil);
  }
}
