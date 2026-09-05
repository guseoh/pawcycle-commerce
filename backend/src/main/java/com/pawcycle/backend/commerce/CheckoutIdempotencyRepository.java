package com.pawcycle.backend.commerce;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckoutIdempotencyRepository
    extends JpaRepository<CheckoutIdempotencyEntity, CheckoutIdempotencyId> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select result from CheckoutIdempotencyEntity result"
          + " where result.id.memberId = :memberId and result.id.idempotencyKey = :idempotencyKey")
  Optional<CheckoutIdempotencyEntity> findForUpdate(
      @Param("memberId") long memberId, @Param("idempotencyKey") String idempotencyKey);

  @Modifying
  @Query(
      "update CheckoutIdempotencyEntity result set result.requestCartVersion = :cartVersion"
          + " where result.id.memberId = :memberId"
          + " and result.id.idempotencyKey = :idempotencyKey"
          + " and result.requestCartVersion is null")
  int saveCartVersionIfAbsent(
      @Param("memberId") long memberId,
      @Param("idempotencyKey") String idempotencyKey,
      @Param("cartVersion") long cartVersion);
}
