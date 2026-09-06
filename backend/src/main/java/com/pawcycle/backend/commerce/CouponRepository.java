package com.pawcycle.backend.commerce;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponRepository extends JpaRepository<CouponEntity, Long> {
  List<CouponEntity> findAllByOrderByIdAsc();
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select coupon from CouponEntity coupon where coupon.id = :couponId")
  Optional<CouponEntity> findByIdForUpdate(@Param("couponId") long couponId);
}
