package com.pawcycle.backend.commerce;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartRepository extends JpaRepository<CartEntity, Long> {
  Optional<CartEntity> findByMemberId(long memberId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select cart from CartEntity cart where cart.memberId = :memberId")
  Optional<CartEntity> findByMemberIdForUpdate(@Param("memberId") long memberId);
}
