package com.pawcycle.backend.commerce;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingPaymentMethodPreparationRepository
    extends JpaRepository<BillingPaymentMethodPreparationEntity, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select preparation from BillingPaymentMethodPreparationEntity preparation "
          + "where preparation.prepareToken = :token and preparation.memberId = :memberId")
  Optional<BillingPaymentMethodPreparationEntity> findForUpdate(
      @Param("token") String token, @Param("memberId") long memberId);

  @Modifying
  @Query(
      "update BillingPaymentMethodPreparationEntity preparation set preparation.status = 'PROCESSING', "
          + "preparation.claimedAt = :claimedAt where preparation.prepareToken = :token "
          + "and preparation.memberId = :memberId and preparation.status = 'READY' "
          + "and preparation.expiresAt > :now")
  int claimReady(
      @Param("token") String token,
      @Param("memberId") long memberId,
      @Param("claimedAt") LocalDateTime claimedAt,
      @Param("now") LocalDateTime now);
}
