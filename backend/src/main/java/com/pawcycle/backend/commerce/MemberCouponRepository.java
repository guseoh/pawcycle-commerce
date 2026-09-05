package com.pawcycle.backend.commerce;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberCouponRepository extends JpaRepository<MemberCouponEntity, Long> {
  List<MemberCouponEntity> findByMemberIdOrderByIdDesc(long memberId);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select coupon from MemberCouponEntity coupon where coupon.id = :memberCouponId")
  Optional<MemberCouponEntity> findByIdForUpdate(@Param("memberCouponId") long memberCouponId);

  @Modifying
  @Query(
      "update MemberCouponEntity coupon set coupon.status = 'RESERVED', coupon.reservedOrderId = :orderId"
          + " where coupon.id = :memberCouponId and coupon.memberId = :memberId and coupon.status = 'AVAILABLE'")
  int reserveIfAvailable(
      @Param("orderId") long orderId,
      @Param("memberCouponId") long memberCouponId,
      @Param("memberId") long memberId);

  @Modifying
  @Query(
      "update MemberCouponEntity coupon set coupon.status = 'USED', coupon.usedAt = :now"
          + " where coupon.reservedOrderId = :orderId and coupon.status = 'RESERVED'")
  int useReserved(@Param("orderId") long orderId, @Param("now") LocalDateTime now);

  @Modifying
  @Query(
      "update MemberCouponEntity coupon set coupon.status = 'AVAILABLE', coupon.reservedOrderId = null"
          + " where coupon.reservedOrderId = :orderId and coupon.status = 'RESERVED'")
  int releaseReserved(@Param("orderId") long orderId);
}
