package com.pawcycle.backend.commerce;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartRepository extends JpaRepository<CartEntity, Long> {
  Optional<CartEntity> findByMemberId(long memberId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select cart from CartEntity cart where cart.memberId = :memberId")
  Optional<CartEntity> findByMemberIdForUpdate(@Param("memberId") long memberId);

  @Modifying
  @Query(
      value =
          """
          INSERT INTO carts(member_id,created_at,updated_at)
          VALUES (:memberId,:now,:now)
          ON DUPLICATE KEY UPDATE id=id
          """,
      nativeQuery = true)
  int ensureExists(@Param("memberId") long memberId, @Param("now") LocalDateTime now);
}
