package com.pawcycle.backend.commerce;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/** Typed HTTP input boundary; conversion for legacy SQL bindings remains in application services. */
public final class CommerceRequests {
	private static final DateTimeFormatter LEGACY_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private CommerceRequests() { }
	public record CartItem(@NotNull @Positive Long skuId, @NotNull @Positive Integer quantity) { }
	public record Quantity(@NotNull @Positive Integer quantity) { }
	public record Checkout(@NotNull @Positive Long addressId, @Positive Long memberCouponId, @Min(0) Long cartVersion) { }
	public record Confirm(@NotBlank String paymentKey, @NotBlank String providerOrderId, @NotNull @DecimalMin("0.00") BigDecimal amount) { }
	public record BillingComplete(@NotBlank String prepareToken, @NotBlank String authKey) { }
	public record Adjustment(@NotNull Integer delta) { }
	public record CouponIssue(@NotNull @Positive Long memberId) { }
	public record Reason(@NotBlank @Size(max=500) String reason) { }
	public record Ship(@NotBlank @Size(max=50) String carrierCode, @NotBlank @Size(max=100) String trackingNumber) { }
	public record Receive(@NotNull Boolean restock) { }
	public record Address(
			@Size(max=100) String name,
			@NotBlank @Size(max=100) String recipientName,
			@NotBlank @Size(max=30) String recipientPhone,
			@NotBlank @Size(max=20) String postalCode,
			@NotBlank @Size(max=255) String addressLine1,
			@Size(max=255) String addressLine2) {
		Map<String, Object> legacyPayload() {
			Map<String, Object> values = new LinkedHashMap<>();
			values.put("name", name); values.put("recipientName", recipientName); values.put("recipientPhone", recipientPhone);
			values.put("postalCode", postalCode); values.put("addressLine1", addressLine1); values.put("addressLine2", addressLine2);
			return values;
		}
	}
	public record Coupon(
			@NotBlank @Size(max=100) String name,
			@NotBlank @Pattern(regexp = "FIXED_AMOUNT|PERCENTAGE") String discountType,
			@NotNull @DecimalMin("0.00") BigDecimal discountValue,
			@NotNull @DecimalMin("0.00") BigDecimal minimumOrderAmount,
			@DecimalMin("0.00") BigDecimal maximumDiscountAmount,
			@NotNull java.time.LocalDateTime validFrom,
			@NotNull java.time.LocalDateTime validUntil,
			@NotNull Boolean active) {
		@AssertTrue
		public boolean isValidRange() {
			return validFrom == null || validUntil == null || validFrom.isBefore(validUntil);
		}

		Map<String, Object> legacyPayload() {
			Map<String, Object> values = new LinkedHashMap<>();
			values.put("name", name); values.put("discountType", discountType); values.put("discountValue", discountValue);
			values.put("minimumOrderAmount", minimumOrderAmount); values.put("maximumDiscountAmount", maximumDiscountAmount);
			values.put("validFrom", validFrom == null ? null : validFrom.format(LEGACY_TIMESTAMP_FORMAT));
			values.put("validUntil", validUntil == null ? null : validUntil.format(LEGACY_TIMESTAMP_FORMAT));
			values.put("active", active);
			return values;
		}
	}
	public record MembershipGrade(
			@NotBlank @Size(max=30) String code,
			@NotBlank @Size(max=100) String name,
			@NotNull @DecimalMin("0.00") BigDecimal minimumPurchaseAmount,
			@NotNull Integer displayOrder,
			@NotNull Boolean active,
			Long benefitCouponId) {
		Map<String, Object> legacyPayload() {
			Map<String, Object> values = new LinkedHashMap<>();
			values.put("code", code); values.put("name", name); values.put("minimumPurchaseAmount", minimumPurchaseAmount);
			values.put("displayOrder", displayOrder); values.put("active", active); values.put("benefitCouponId", benefitCouponId);
			return values;
		}
	}
}
