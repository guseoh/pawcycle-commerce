package com.pawcycle.backend.commerce.coupon.persistence;

import com.pawcycle.backend.commerce.CommerceException;
import com.pawcycle.backend.commerce.CouponEntity;
import com.pawcycle.backend.commerce.CouponRepository;
import com.pawcycle.backend.commerce.CouponRequest;
import com.pawcycle.backend.commerce.MemberCouponEntity;
import com.pawcycle.backend.commerce.MemberCouponRepository;
import com.pawcycle.backend.member.persistence.MemberRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CouponPersistenceAdapter {
  private final CouponRepository coupons;
  private final MemberCouponRepository memberCoupons;
  private final MemberRepository members;
  private final Clock clock;

  public CouponPersistenceAdapter(
      CouponRepository coupons,
      MemberCouponRepository memberCoupons,
      MemberRepository members,
      Clock clock) {
    this.coupons = coupons;
    this.memberCoupons = memberCoupons;
    this.members = members;
    this.clock = clock;
  }

  @Transactional
  public long create(CouponRequest request) {
    return coupons
        .saveAndFlush(
            new CouponEntity(
                request.name(),
                request.discountType(),
                request.discountValue(),
                request.minimumOrderAmount(),
                request.maximumDiscountAmount(),
                request.validFrom().withNano(0),
                request.validUntil().withNano(0),
                request.active()))
        .getId();
  }

  @Transactional
  public void update(long couponId, CouponRequest request) {
    CouponEntity coupon = requireCoupon(couponId);
    coupon.update(
        request.name(),
        request.discountType(),
        request.discountValue(),
        request.minimumOrderAmount(),
        request.maximumDiscountAmount(),
        request.validFrom().withNano(0),
        request.validUntil().withNano(0),
        request.active());
    coupons.flush();
  }

  @Transactional
  public void issue(long couponId, long memberId) {
    requireCoupon(couponId);
    if (!members.existsById(memberId))
      throw new CommerceException(404, "MEMBER_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    memberCoupons.saveAndFlush(new MemberCouponEntity(memberId, couponId, now()));
  }

  @Transactional(readOnly = true)
  public List<CouponView> findAll() {
    return coupons.findAllByOrderByIdAsc().stream()
        .map(
            coupon ->
                new CouponView(
                    coupon.getId(),
                    coupon.getName(),
                    coupon.getDiscountType(),
                    coupon.getDiscountValue(),
                    coupon.getMinimumOrderAmount(),
                    coupon.getMaximumDiscountAmount(),
                    timestamp(coupon.getValidFrom()),
                    timestamp(coupon.getValidUntil()),
                    coupon.isActive()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<MemberCouponView> findForMember(long memberId) {
    return memberCoupons.findByMemberIdOrderByIdDesc(memberId).stream()
        .map(
            memberCoupon -> {
              CouponEntity coupon = memberCoupon.getCoupon();
              return new MemberCouponView(
                  memberCoupon.getId(),
                  coupon.getId(),
                  coupon.getName(),
                  coupon.getDiscountType(),
                  coupon.getDiscountValue(),
                  memberCoupon.getStatus(),
                  timestamp(coupon.getValidFrom()),
                  timestamp(coupon.getValidUntil()));
            })
        .toList();
  }

  private CouponEntity requireCoupon(long couponId) {
    return coupons
        .findById(couponId)
        .orElseThrow(
            () -> new CommerceException(404, "COUPON_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."));
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault());
  }

  private Timestamp timestamp(LocalDateTime value) {
    return Timestamp.valueOf(value);
  }
}
