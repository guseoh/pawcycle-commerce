package com.pawcycle.backend.commerce;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {
  Optional<PaymentEntity> findByProviderOrderId(String providerOrderId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select payment from PaymentEntity payment where payment.providerOrderId = :providerOrderId")
  Optional<PaymentEntity> findByProviderOrderIdForUpdate(@Param("providerOrderId") String providerOrderId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select payment from PaymentEntity payment where payment.id = :paymentId")
  Optional<PaymentEntity> findByIdForUpdate(@Param("paymentId") long paymentId);

  @Query(
      "select payment.id from PaymentEntity payment where payment.type = 'NORMAL' "
          + "and payment.status = 'READY' and payment.expiresAt is not null "
          + "and payment.expiresAt <= :now order by payment.expiresAt, payment.id")
  List<Long> findDuePaymentIds(@Param("now") LocalDateTime now, Pageable pageable);
}
