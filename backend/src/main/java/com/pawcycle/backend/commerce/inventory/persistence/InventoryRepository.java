package com.pawcycle.backend.commerce.inventory.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<InventoryEntity, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select inventory from InventoryEntity inventory where inventory.skuId = :skuId")
  Optional<InventoryEntity> findLockedBySkuId(@Param("skuId") long skuId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update InventoryEntity inventory
      set inventory.availableQuantity = inventory.availableQuantity - :quantity,
          inventory.reservedQuantity = inventory.reservedQuantity + :quantity,
          inventory.version = inventory.version + 1
      where inventory.skuId = :skuId
        and inventory.version = :expectedVersion
        and inventory.availableQuantity >= :quantity
      """)
  int reserveIfVersionMatches(
      @Param("skuId") long skuId,
      @Param("quantity") int quantity,
      @Param("expectedVersion") long expectedVersion);

  @Query(
      """
      select inventory.skuId as skuId,
             inventory.availableQuantity as availableQuantity,
             inventory.reservedQuantity as reservedQuantity,
             inventory.version as version,
             inventory.sku.skuCode as skuCode
      from InventoryEntity inventory
      order by inventory.skuId
      """)
  List<InventoryAdminProjection> findAdminProjections();
}