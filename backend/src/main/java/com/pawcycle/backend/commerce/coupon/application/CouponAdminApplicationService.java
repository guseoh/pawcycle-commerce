package com.pawcycle.backend.commerce.coupon.application;

import com.pawcycle.backend.commerce.AdminAuditService;
import com.pawcycle.backend.commerce.CouponRequest;
import com.pawcycle.backend.commerce.coupon.persistence.CouponPersistenceAdapter;
import com.pawcycle.backend.commerce.coupon.persistence.CouponView;
import com.pawcycle.backend.common.error.FieldErrorResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponAdminApplicationService {
  private final CouponPersistenceAdapter coupons;
  private final AdminAuditService audits;

  public CouponAdminApplicationService(CouponPersistenceAdapter coupons, AdminAuditService audits) {
    this.coupons = coupons;
    this.audits = audits;
  }

  @Transactional
  public long create(long adminId, CouponRequest request) {
    long couponId = coupons.create(request);
    audits.append(adminId, "COUPON_CREATE", "COUPON", couponId);
    return couponId;
  }

  @Transactional
  public void update(long adminId, long couponId, CouponPatchCommand request) {
    CouponView current = coupons.find(couponId);
    List<FieldErrorResponse> errors = validate(request, current);
    if (!errors.isEmpty()) throw new CouponValidationException(errors);
    if (current == null) {
      coupons.require(couponId);
    } else {
      coupons.update(couponId, merged(current, request));
    }
    audits.append(adminId, "COUPON_UPDATE", "COUPON", couponId);
  }

  private static CouponRequest merged(CouponView current, CouponPatchCommand request) {
    return new CouponRequest(
        request.hasName() ? request.name() : current.name(),
        request.hasDiscountType() ? request.discountType() : current.discountType(),
        request.hasDiscountValue() ? request.discountValue() : current.discountValue(),
        request.hasMinimumOrderAmount() ? request.minimumOrderAmount() : current.minimumOrderAmount(),
        request.hasMaximumDiscountAmount() ? request.maximumDiscountAmount() : current.maximumDiscountAmount(),
        request.hasValidFrom() ? request.validFrom() : current.validFrom().toLocalDateTime(),
        request.hasValidUntil() ? request.validUntil() : current.validUntil().toLocalDateTime(),
        request.hasActive() ? request.active() : current.active());
  }

  private static List<FieldErrorResponse> validate(CouponPatchCommand request, CouponView current) {
    List<FieldErrorResponse> errors = new ArrayList<>();
    if (request.isEmpty()) errors.add(new FieldErrorResponse("request", "하나 이상의 필드를 입력해야 합니다."));
    if (request.hasName() && (request.name() == null || request.name().isBlank())) errors.add(new FieldErrorResponse("name", "필수입니다."));
    if (request.hasName() && request.name() != null && request.name().length() > 100) errors.add(new FieldErrorResponse("name", "크기는 100자 이하여야 합니다."));
    if (request.hasDiscountType() && (request.discountType() == null || !Pattern.matches("FIXED_AMOUNT|PERCENTAGE", request.discountType()))) errors.add(new FieldErrorResponse("discountType", "할인 방식이 올바르지 않습니다."));
    if (request.hasDiscountValue() && (request.discountValue() == null || request.discountValue().signum() < 0)) errors.add(new FieldErrorResponse("discountValue", "0 이상이어야 합니다."));
    if (request.hasMinimumOrderAmount() && (request.minimumOrderAmount() == null || request.minimumOrderAmount().signum() < 0)) errors.add(new FieldErrorResponse("minimumOrderAmount", "0 이상이어야 합니다."));
    if (request.hasMaximumDiscountAmount() && request.maximumDiscountAmount() != null && request.maximumDiscountAmount().signum() < 0) errors.add(new FieldErrorResponse("maximumDiscountAmount", "0 이상이어야 합니다."));
    if (request.hasValidFrom() && request.validFrom() == null) errors.add(new FieldErrorResponse("validFrom", "필수입니다."));
    if (request.hasValidUntil() && request.validUntil() == null) errors.add(new FieldErrorResponse("validUntil", "필수입니다."));
    if (request.hasActive() && request.active() == null) errors.add(new FieldErrorResponse("active", "필수입니다."));
    if (current != null && errors.stream().noneMatch(error -> error.field().equals("validFrom") || error.field().equals("validUntil"))) {
      LocalDateTimePair range = new LocalDateTimePair(request.hasValidFrom() ? request.validFrom() : current.validFrom().toLocalDateTime(), request.hasValidUntil() ? request.validUntil() : current.validUntil().toLocalDateTime());
      if (range.from() != null && range.until() != null && !range.from().isBefore(range.until())) errors.add(new FieldErrorResponse("validUntil", "validFrom보다 이후여야 합니다."));
    }
    return errors.stream().sorted(Comparator.comparing(FieldErrorResponse::field).thenComparing(FieldErrorResponse::message)).toList();
  }

  private record LocalDateTimePair(java.time.LocalDateTime from, java.time.LocalDateTime until) {}

  @Transactional
  public void issue(long adminId, long couponId, long memberId) {
    coupons.issue(couponId, memberId);
    audits.append(adminId, "COUPON_ISSUE", "COUPON", couponId);
  }

  @Transactional(readOnly = true)
  public List<CouponView> list() {
    return coupons.findAll();
  }
}
