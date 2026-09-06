package com.pawcycle.backend.commerce.coupon.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.commerce.AdminAuditService;
import com.pawcycle.backend.commerce.CouponRequest;
import com.pawcycle.backend.commerce.coupon.persistence.CouponPersistenceAdapter;
import com.pawcycle.backend.commerce.coupon.persistence.CouponView;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.TimeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CouponAdminApplicationServiceTests {
  private CouponPersistenceAdapter coupons;
  private AdminAuditService audits;
  private CouponAdminApplicationService service;
  private CouponView current;

  @BeforeEach
  void setUp() {
    coupons = mock(CouponPersistenceAdapter.class);
    audits = mock(AdminAuditService.class);
    service = new CouponAdminApplicationService(coupons, audits);
    current =
        new CouponView(
            7L,
            "기존 쿠폰",
            "PERCENTAGE",
            new BigDecimal("10"),
            new BigDecimal("1000"),
            new BigDecimal("5000"),
            Timestamp.from(Instant.parse("2026-08-01T00:00:00Z")),
            Timestamp.from(Instant.parse("2026-09-01T00:00:00Z")),
            true);
    when(coupons.findForUpdate(7L)).thenReturn(current);
  }

  @Test
  void nameOnlyPatchPreservesUnchangedFieldsUsingLockedCurrentState() {
    CouponPatchCommand patch = namePatch("새 쿠폰");

    service.update(1L, 7L, patch);

    verify(coupons).findForUpdate(7L);
    CouponRequest merged = updatedRequest();
    assertThat(merged.name()).isEqualTo("새 쿠폰");
    assertThat(merged.discountType()).isEqualTo("PERCENTAGE");
    assertThat(merged.discountValue()).isEqualByComparingTo("10");
    assertThat(merged.validFrom()).isEqualTo(LocalDateTime.of(2026, 8, 1, 0, 0));
    assertThat(merged.validUntil()).isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0));
    assertThat(merged.active()).isTrue();
  }

  @Test
  void nameOnlyPatchPreservesUtcTimesUnderNonUtcJvmDefaultZone() {
    TimeZone original = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));

      service.update(1L, 7L, namePatch("시간대 무관"));

      CouponRequest merged = updatedRequest();
      assertThat(merged.validFrom()).isEqualTo(LocalDateTime.of(2026, 8, 1, 0, 0));
      assertThat(merged.validUntil()).isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0));
    } finally {
      TimeZone.setDefault(original);
    }
  }

  @Test
  void activeOnlyPatchUpdatesBoolean() {
    CouponPatchCommand patch = activePatch(false);

    service.update(1L, 7L, patch);

    assertThat(updatedRequest().active()).isFalse();
  }

  @Test
  void explicitNullClearsNullableMaximumDiscountAmount() {
    CouponPatchCommand patch = maximumDiscountAmountPatch(null);

    service.update(1L, 7L, patch);

    assertThat(updatedRequest().maximumDiscountAmount()).isNull();
  }

  @Test
  void fullPayloadRemainsSupported() {
    CouponPatchCommand patch =
        new CouponPatchCommand(
            "전체 수정",
            "FIXED_AMOUNT",
            new BigDecimal("2500"),
            new BigDecimal("3000"),
            null,
            LocalDateTime.of(2026, 10, 1, 0, 0),
            LocalDateTime.of(2026, 11, 1, 0, 0),
            false,
            true,
            true,
            true,
            true,
            true,
            true,
            true,
            true);

    service.update(1L, 7L, patch);

    CouponRequest merged = updatedRequest();
    assertThat(merged.name()).isEqualTo("전체 수정");
    assertThat(merged.discountType()).isEqualTo("FIXED_AMOUNT");
    assertThat(merged.validFrom()).isEqualTo(LocalDateTime.of(2026, 10, 1, 0, 0));
    assertThat(merged.validUntil()).isEqualTo(LocalDateTime.of(2026, 11, 1, 0, 0));
    assertThat(merged.active()).isFalse();
  }

  @Test
  void emptyPatchIsRejectedWithMeaningfulFieldError() {
    assertThatThrownBy(() -> service.update(1L, 7L, emptyPatch()))
        .isInstanceOfSatisfying(
            CouponValidationException.class,
            exception ->
                assertThat(exception.fieldErrors())
                    .anyMatch(error -> error.field().equals("request")));
  }

  @Test
  void invalidFinalDateRangeIsRejectedAfterPatchMerge() {
    CouponPatchCommand patch =
        new CouponPatchCommand(
            null,
            null,
            null,
            null,
            null,
            null,
            LocalDateTime.of(2026, 7, 1, 0, 0),
            null,
            false,
            false,
            false,
            false,
            false,
            false,
            true,
            false);

    assertThatThrownBy(() -> service.update(1L, 7L, patch))
        .isInstanceOf(CouponValidationException.class);
  }

  @Test
  void invalidSuppliedFieldIsRejectedBeforePersistence() {
    CouponPatchCommand patch =
        new CouponPatchCommand(
            null,
            null,
            new BigDecimal("-1"),
            null,
            null,
            null,
            null,
            null,
            false,
            false,
            true,
            false,
            false,
            false,
            false,
            false);

    assertThatThrownBy(() -> service.update(1L, 7L, patch))
        .isInstanceOf(CouponValidationException.class);
  }

  @Test
  void validPatchForUnknownCouponKeepsNotFoundContract() {
    when(coupons.findForUpdate(999L)).thenReturn(null);
    CouponPatchCommand patch = namePatch("없음");

    service.update(1L, 999L, patch);

    verify(coupons).require(999L);
  }

  private CouponRequest updatedRequest() {
    ArgumentCaptor<CouponRequest> captor = ArgumentCaptor.forClass(CouponRequest.class);
    verify(coupons).update(eq(7L), captor.capture());
    return captor.getValue();
  }

  private static CouponPatchCommand emptyPatch() {
    return new CouponPatchCommand(null, null, null, null, null, null, null, null, false, false, false, false, false, false, false, false);
  }

  private static CouponPatchCommand namePatch(String name) {
    return new CouponPatchCommand(
        name, null, null, null, null, null, null, null, true, false, false, false, false, false, false, false);
  }

  private static CouponPatchCommand activePatch(boolean active) {
    return new CouponPatchCommand(
        null, null, null, null, null, null, null, active, false, false, false, false, false, false, false, true);
  }

  private static CouponPatchCommand maximumDiscountAmountPatch(BigDecimal value) {
    return new CouponPatchCommand(
        null, null, null, null, value, null, null, null, false, false, false, false, true, false, false, false);
  }
}
