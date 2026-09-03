package com.pawcycle.backend.commerce.inventory.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Timestamp;

@Entity
@Table(name = "inventory_movements")
public class InventoryMovementEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "sku_id", nullable = false)
  private Long skuId;

  @Column(name = "payment_id")
  private Long paymentId;

  @Column(nullable = false, length = 20)
  private String type;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "available_before", nullable = false)
  private int availableBefore;

  @Column(name = "available_after", nullable = false)
  private int availableAfter;

  @Column(name = "reserved_before", nullable = false)
  private int reservedBefore;

  @Column(name = "reserved_after", nullable = false)
  private int reservedAfter;

  @Column(name = "cancellation_id")
  private Long cancellationId;

  @Column(name = "return_id")
  private Long returnId;

  @Column(name = "source_id")
  private Long sourceId;

  @Column(name = "created_at", nullable = false)
  private Timestamp createdAt;

  protected InventoryMovementEntity() {}

  private InventoryMovementEntity(
      long skuId,
      Long paymentId,
      String type,
      int quantity,
      int availableBefore,
      int availableAfter,
      int reservedBefore,
      int reservedAfter,
      Long cancellationId,
      Long returnId,
      Long sourceId,
      Timestamp createdAt) {
    this.skuId = skuId;
    this.paymentId = paymentId;
    this.type = type;
    this.quantity = quantity;
    this.availableBefore = availableBefore;
    this.availableAfter = availableAfter;
    this.reservedBefore = reservedBefore;
    this.reservedAfter = reservedAfter;
    this.cancellationId = cancellationId;
    this.returnId = returnId;
    this.sourceId = sourceId;
    this.createdAt = createdAt;
  }

  public static InventoryMovementEntity payment(
      long skuId,
      long paymentId,
      String type,
      int quantity,
      int availableBefore,
      int availableAfter,
      int reservedBefore,
      int reservedAfter,
      Timestamp createdAt) {
    return new InventoryMovementEntity(
        skuId,
        paymentId,
        type,
        quantity,
        availableBefore,
        availableAfter,
        reservedBefore,
        reservedAfter,
        null,
        null,
        paymentId,
        createdAt);
  }

  public static InventoryMovementEntity adminAdjustment(
      long skuId,
      int quantity,
      int availableBefore,
      int availableAfter,
      int reservedBefore,
      int reservedAfter,
      Timestamp createdAt) {
    return new InventoryMovementEntity(
        skuId,
        null,
        "ADMIN_ADJUST",
        quantity,
        availableBefore,
        availableAfter,
        reservedBefore,
        reservedAfter,
        null,
        null,
        null,
        createdAt);
  }

  public static InventoryMovementEntity cancellationRestore(
      long skuId,
      long cancellationId,
      int quantity,
      int availableBefore,
      int availableAfter,
      int reservedBefore,
      int reservedAfter,
      Timestamp createdAt) {
    return new InventoryMovementEntity(
        skuId,
        null,
        "CANCEL_RESTORE",
        quantity,
        availableBefore,
        availableAfter,
        reservedBefore,
        reservedAfter,
        cancellationId,
        null,
        cancellationId,
        createdAt);
  }

  public static InventoryMovementEntity returnRestore(
      long skuId,
      long returnId,
      int quantity,
      int availableBefore,
      int availableAfter,
      int reservedBefore,
      int reservedAfter,
      Timestamp createdAt) {
    return new InventoryMovementEntity(
        skuId,
        null,
        "RETURN_RESTORE",
        quantity,
        availableBefore,
        availableAfter,
        reservedBefore,
        reservedAfter,
        null,
        returnId,
        returnId,
        createdAt);
  }
}