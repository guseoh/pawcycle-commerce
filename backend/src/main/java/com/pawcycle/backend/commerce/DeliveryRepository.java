package com.pawcycle.backend.commerce;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryRepository extends JpaRepository<DeliveryEntity, Long> {
  @Modifying
  @Query(
      value = "INSERT INTO deliveries(order_id,status) VALUES (:orderId,'PREPARING') ON DUPLICATE KEY UPDATE id=id",
      nativeQuery = true)
  int insertPreparing(@Param("orderId") long orderId);

  Optional<DeliveryEntity> findByOrderId(Long orderId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select delivery from DeliveryEntity delivery where delivery.id = :deliveryId")
  Optional<DeliveryEntity> findByIdForUpdate(@Param("deliveryId") long deliveryId);
}
