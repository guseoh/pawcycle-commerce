package com.pawcycle.backend.commerce;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommerceOrderRepository extends JpaRepository<CommerceOrderEntity, Long> {
  List<CommerceOrderEntity> findAllByOrderByIdDesc();
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select order from CommerceOrderEntity order where order.id = :orderId")
  Optional<CommerceOrderEntity> findByIdForUpdate(@Param("orderId") long orderId);
}
