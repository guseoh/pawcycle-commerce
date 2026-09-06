package com.pawcycle.backend.commerce;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingPaymentMethodRepository
    extends JpaRepository<BillingPaymentMethodEntity, Long> {
  long countByMemberIdAndStatus(Long memberId, String status);

  @Modifying
  @Query(
      "update BillingPaymentMethodEntity method set method.status = 'REVOKED', "
          + "method.revokedAt = :now where method.memberId = :memberId and method.status = 'ACTIVE'")
  int revokeActive(@Param("memberId") long memberId, @Param("now") LocalDateTime now);
}
