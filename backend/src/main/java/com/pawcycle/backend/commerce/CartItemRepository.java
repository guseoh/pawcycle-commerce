package com.pawcycle.backend.commerce;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends JpaRepository<CartItemEntity, CartItemId> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select item from CartItemEntity item where item.id = :id")
  Optional<CartItemEntity> findByIdForUpdate(@Param("id") CartItemId id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select item from CartItemEntity item where item.id.cartId = :cartId")
  List<CartItemEntity> findAllByCartIdForUpdate(@Param("cartId") long cartId);
}
