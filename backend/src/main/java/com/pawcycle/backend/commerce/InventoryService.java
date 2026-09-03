package com.pawcycle.backend.commerce;

import com.pawcycle.backend.commerce.inventory.persistence.InventoryEntity;
import com.pawcycle.backend.commerce.inventory.persistence.InventoryMovementEntity;
import com.pawcycle.backend.commerce.inventory.persistence.InventoryMovementRepository;
import com.pawcycle.backend.commerce.inventory.persistence.InventoryRepository;
import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Inventory mutations and their movement audit are one transaction owned by the caller. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class InventoryService {
  private final InventoryRepository inventories;
  private final InventoryMovementRepository movements;
  private final Clock clock;

  public InventoryService(
      InventoryRepository inventories, InventoryMovementRepository movements, Clock clock) {
    this.inventories = inventories;
    this.movements = movements;
    this.clock = clock;
  }

  public void reserve(long skuId, int quantity, long paymentId) {
    requirePositiveQuantity(quantity);
    InventoryEntity inventory = inventories.findById(skuId).orElse(null);
    if (inventory == null || inventory.availableQuantity() < quantity) {
      throw new CommerceException(409, "INVENTORY_INSUFFICIENT", "재고가 부족합니다.");
    }

    int availableBefore = inventory.availableQuantity();
    int reservedBefore = inventory.reservedQuantity();
    int changed =
        inventories.reserveIfVersionMatches(skuId, quantity, inventory.version());
    if (changed != 1) {
      throw new CommerceException(409, "INVENTORY_CONFLICT", "재고가 변경되었습니다.");
    }

    movements.save(
        InventoryMovementEntity.payment(
            skuId,
            paymentId,
            "RESERVE",
            quantity,
            availableBefore,
            availableBefore - quantity,
            reservedBefore,
            reservedBefore + quantity,
            now()));
  }

  public void release(long skuId, int quantity, long paymentId) {
    requirePositiveQuantity(quantity);
    InventoryEntity inventory = lock(skuId, false);
    requireReservedQuantity(inventory, quantity);

    int availableBefore = inventory.availableQuantity();
    int reservedBefore = inventory.reservedQuantity();
    inventory.release(quantity);
    movements.save(
        InventoryMovementEntity.payment(
            skuId,
            paymentId,
            "RELEASE",
            quantity,
            availableBefore,
            inventory.availableQuantity(),
            reservedBefore,
            inventory.reservedQuantity(),
            now()));
  }

  public void deduct(long skuId, int quantity, long paymentId) {
    requirePositiveQuantity(quantity);
    InventoryEntity inventory = lock(skuId, false);
    requireReservedQuantity(inventory, quantity);

    int availableBefore = inventory.availableQuantity();
    int reservedBefore = inventory.reservedQuantity();
    inventory.deduct(quantity);
    movements.save(
        InventoryMovementEntity.payment(
            skuId,
            paymentId,
            "DEDUCT",
            quantity,
            availableBefore,
            inventory.availableQuantity(),
            reservedBefore,
            inventory.reservedQuantity(),
            now()));
  }

  public void restoreCancellation(long skuId, int quantity, long cancellationId) {
    restore(skuId, quantity, cancellationId, true);
  }

  public void restoreReturn(long skuId, int quantity, long returnId) {
    restore(skuId, quantity, returnId, false);
  }

  public void adjust(long skuId, int delta) {
    InventoryEntity inventory = lock(skuId, true);
    int availableBefore = inventory.availableQuantity();
    if ((long) availableBefore + delta < 0) {
      throw new CommerceException(409, "INVENTORY_INSUFFICIENT", "재고가 부족합니다.");
    }

    int reservedBefore = inventory.reservedQuantity();
    inventory.adjust(delta);
    if (delta != 0) {
      movements.save(
          InventoryMovementEntity.adminAdjustment(
              skuId,
              Math.abs(delta),
              availableBefore,
              inventory.availableQuantity(),
              reservedBefore,
              inventory.reservedQuantity(),
              now()));
    }
  }

  private void restore(long skuId, int quantity, long sourceId, boolean cancellation) {
    requirePositiveQuantity(quantity);
    InventoryEntity inventory = lock(skuId, false);
    int availableBefore = inventory.availableQuantity();
    int reservedBefore = inventory.reservedQuantity();
    inventory.restore(quantity);

    InventoryMovementEntity movement =
        cancellation
            ? InventoryMovementEntity.cancellationRestore(
                skuId,
                sourceId,
                quantity,
                availableBefore,
                inventory.availableQuantity(),
                reservedBefore,
                inventory.reservedQuantity(),
                now())
            : InventoryMovementEntity.returnRestore(
                skuId,
                sourceId,
                quantity,
                availableBefore,
                inventory.availableQuantity(),
                reservedBefore,
                inventory.reservedQuantity(),
                now());
    movements.save(movement);
  }

  private InventoryEntity lock(long skuId, boolean notFoundIsClientError) {
    return inventories
        .findLockedBySkuId(skuId)
        .orElseThrow(
            () -> {
              if (notFoundIsClientError) {
                return new CommerceException(
                    404, "INVENTORY_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
              }
              return new IllegalStateException("Inventory is missing");
            });
  }

  private static void requirePositiveQuantity(int quantity) {
    if (quantity <= 0) {
      throw new IllegalArgumentException("quantity must be positive");
    }
  }

  private static void requireReservedQuantity(InventoryEntity inventory, int quantity) {
    if (inventory.reservedQuantity() < quantity) {
      throw new IllegalStateException("Reserved inventory is inconsistent");
    }
  }

  private Timestamp now() {
    return Timestamp.from(clock.instant());
  }
}